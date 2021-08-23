import java.util.*
import kotlin.Comparator

// Code for a simple task manager, implemented using composition to try out Kotlins support
// Using Result returns to try how that fits with Kotlin (spoiler: not that great) instead of exceptions

enum class Priority {
    LOW, MEDIUM, HIGH
}

data class Process (val PID: Int, val priority: Priority) {
    internal var index : Int = -1
}

// Inspired by kotlin-result, but dumbed down to fit the scope here
data class Result<T, E>(
    val data: T? = null,
    val error: E? = null
)

sealed class Msg
// A return Msg signaling Ok
object Ok : Msg()
// A return Msg signaling Ok and the evicted process
class Evicted(val process : Process?) : Msg()

// Error definitions
sealed class Error
object ProcessListFull : Error()
object DuplicatePID : Error()
object PIDNotFound : Error()
class UnableToKill(val PID : Int) : Error()

// Sorting order parameters, pass any combination of them: like list(PRIORITY_ORDER.then(TIME_ORDER))
val PID_ORDER = compareBy<Process> { it.PID }
val PRIORITY_ORDER = compareByDescending<Process> { it.priority }
val TIME_ORDER = compareBy<Process> { it.index }

const val MAX_TASK_CAPACITY = 6

// TODO: Actual impl should prob allow for platform specific kill of actual process
interface TaskManagerInterface {
    fun add(process : Process) : Result<Evicted, Error>
    fun list() : Result<List<Process>, Error>
    fun list(comparator : Comparator<Process>) : Result<List<Process>, Error>
    fun kill(PID : Int) : Result<Msg, Error>
    fun killGroup(priorityGroup : Priority) : Result<Msg, Error>
    fun killAll() : Result<Msg, Error>
}

class TaskManager : TaskManagerInterface {

    internal val processList : MutableList<Process> = ArrayList<Process>(MAX_TASK_CAPACITY)

    override fun add(process : Process) : Result<Evicted, Error> {
        synchronized(processList) {
            if (processList.size >= MAX_TASK_CAPACITY) {
                return Result(null, ProcessListFull)
            }
            if (processList.any { p -> p.PID == process.PID })
                return Result(null, DuplicatePID)
            process.index = processList.size
            processList.add(process)
        }
        return Result(Evicted(null))
    }

    override fun list() : Result<List<Process>, Error>  {
        synchronized(processList) {
            return Result(processList.toList())
        }
    }

    override fun list(comparator : Comparator<Process>) : Result<List<Process>, Error>  {
        synchronized(processList) {
            return Result(processList.toList().sortedWith(comparator))
        }
    }

    override fun kill(PID : Int) : Result<Msg, Error> {
        synchronized(processList) {
            val removed = processList.removeIf { p -> p.PID == PID }
            if (!removed)
                return Result(null, PIDNotFound)
        }
        return Result(Ok)
    }

    override fun killGroup(priorityGroup : Priority) : Result<Msg, Error> {
        synchronized(processList) {
            for (p in processList.filter {p -> p.priority == priorityGroup}) {
                val (_,error) = kill(p.PID)
                if (error != null) return Result(null, UnableToKill(p.PID))
            }
        }
        return Result(Ok)
    }

    override fun killAll() : Result<Msg, Error> {
        synchronized(processList) {
            for (pid in processList.map{ i -> i.PID }) {
                val result = kill(pid)
                if (result.error != null) return Result(null, UnableToKill(pid))
            }
        }
        return Result(Ok)
    }
}

class FifoTaskManager(val m : TaskManager) : TaskManagerInterface by m {
    constructor() : this(TaskManager())

    override fun add(process: Process): Result<Evicted, Error> {
        synchronized(m.processList) {
            val evicted : Process? = if (m.processList.size >= MAX_TASK_CAPACITY) {
                val x = m.processList.removeAt(0)
                x
            } else {
                null
            }
            val result = m.add(process)
            return when {
                result.error == null && evicted != null -> Result(Evicted(evicted))
                else -> result
            }
        }
    }
}

class PriorityTaskManager (val m : TaskManager) : TaskManagerInterface by m {
    constructor() : this(TaskManager())

    override fun add(process: Process): Result<Evicted, Error> {
        synchronized(m.processList) {
            val evicted : Process? = if (m.processList.size >= MAX_TASK_CAPACITY) {
                // find lowest prio/index and remove it
                val sorted = m.processList
                    .mapIndexed { index, item -> Pair(item.priority, index) }
                    .sortedWith(compareBy({ it.first }, { it.second }))
                if (sorted[0].first < process.priority) {
                    val x = m.processList.removeAt(sorted[0].second)
                    x
                } else {
                    // if no task has lower priority, return error
                    return Result(null, ProcessListFull)
                }
            } else {
                null
            }
            val result = m.add(process)
            // ensure add succeeds and return the evicted item, if any
            return when {
                result.error == null && evicted != null -> Result(Evicted(evicted))
                else -> result
            }
        }
    }
}
