import kotlin.test.*

internal class TaskManagerTest {

    @Test
    fun `Add one Process and list contains returns one process, and no errors`() {
        val manager = TaskManager()
        val (_, addError) = manager.add(Process(1, Priority.HIGH))
        assertNull(addError)
        val (list, listError) = manager.list()
        assertNull(listError)
        assertEquals(list?.size, 1)
    }

    @Test
    fun `List should sort`() {
        val manager = TaskManager()
        val (_, _) = manager.add(Process(4, Priority.LOW))
        val (_, _) = manager.add(Process(3, Priority.MEDIUM))
        val (_, _) = manager.add(Process(2, Priority.HIGH))
        val (_, _) = manager.add(Process(1, Priority.HIGH))

        val (list, listError) = manager.list(PID_ORDER)
        assertNull(listError)
        assertEquals(list?.get(0)?.PID,1)
    }

    @Test
    fun `List should sort by multiple`() {
        val manager = TaskManager()
        val (_, _) = manager.add(Process(1, Priority.LOW))
        val (_, _) = manager.add(Process(3, Priority.MEDIUM))
        val (_, _) = manager.add(Process(2, Priority.HIGH))
        val (_, _) = manager.add(Process(4, Priority.HIGH))

        val (list, listError) = manager.list(PRIORITY_ORDER.then(TIME_ORDER))
        assertNull(listError)
        assertEquals(list?.get(0)?.PID,2)
    }

    @Test
    fun `Should error if add would lead to exceeding limit`() {
        val manager = TaskManager()
        for (i in 1..MAX_TASK_CAPACITY) {
            val (_, addError) = manager.add(Process(i, Priority.HIGH))
            assertNull(addError)
        }
        val (_, error) = manager.add(Process(1, Priority.HIGH))
        assertEquals(error, ProcessListFull)
    }

    @Test
    fun `Should fail adding duplicate PID`() {
        val manager = TaskManager()
        val (_,_) = manager.add(Process(1, Priority.HIGH))
        val r2 = manager.add(Process(1, Priority.HIGH))
        assertEquals(r2.error, DuplicatePID)
    }

    @Test
    fun `Kill should remove process from list and not error`() {
        val manager = TaskManager()
        val r1 = manager.add(Process(1, Priority.HIGH))
        assertNull(r1.error)
        val (_,error) = manager.kill(1)
        assertNull(error)
    }

    @Test
    fun `Kill should error if process is not found`() {
        val manager = TaskManager()
        val r1 = manager.add(Process(1, Priority.HIGH))
        assertNull(r1.error)
        val (_,error) = manager.kill(2)
        assertEquals(error, PIDNotFound)
    }

    @Test
    fun `FifoTaskManager should remove oldest entry when full`() {
        val manager = FifoTaskManager(TaskManager())
        for (i in 1..MAX_TASK_CAPACITY) {
            val (_, addError) = manager.add(Process(i, Priority.HIGH))
            assertNull(addError)
        }
        val (msg, error) = manager.add(Process(1, Priority.HIGH))
        assertNull(error)
        assertEquals(msg?.process?.PID, 1)
    }

    @Test
    fun `PriorityTaskManager should remove lowest pri and oldest first entry when full`() {
        val manager = PriorityTaskManager()
        for (i in 1 until MAX_TASK_CAPACITY) {
            val (_, addError) = manager.add(Process(i, Priority.LOW))
            assertNull(addError)
        }
        val (_, _) = manager.add(Process(MAX_TASK_CAPACITY, Priority.MEDIUM))
        val (msg, error) = manager.add(Process(MAX_TASK_CAPACITY+1, Priority.HIGH))
        assertNull(error)
        assertEquals(msg?.process?.PID, 1)
    }
    @Test
    fun `PriorityTaskManager should error if no existing process has lower priority`() {
        val manager = PriorityTaskManager()
        for (i in 1..MAX_TASK_CAPACITY) {
            val (_, addError) = manager.add(Process(i, Priority.MEDIUM))
            assertNull(addError)
        }
        val (_, error) = manager.add(Process(MAX_TASK_CAPACITY+1, Priority.MEDIUM))
        assertNotNull(error)
        assertEquals(manager.list().data?.size, MAX_TASK_CAPACITY)
    }

    @Test
    fun `Killall should remove all processes`() {
        val manager = PriorityTaskManager()
        var (_,err) = manager.killAll()
        assertNull(err)
        for (i in 1..MAX_TASK_CAPACITY) {
            val (_, addError) = manager.add(Process(i, Priority.MEDIUM))
            assertNull(addError)
        }
        assertEquals(manager.list().data?.size, MAX_TASK_CAPACITY)

        var (_,error) = manager.killAll()
        assertNull(error)
        assertEquals(manager.list().data?.size, 0)
    }

    @Test
    fun `KillGroup should remove all processes of a certain priority`() {
        val manager = FifoTaskManager()
        var (_,err) = manager.killGroup(Priority.LOW)
        assertNull(err)
        for (i in 1..3) {
            val (_, addError) = manager.add(Process(i, Priority.LOW))
            assertNull(addError)
        }
        for (i in 4..MAX_TASK_CAPACITY) {
            val (_, addError) = manager.add(Process(i, Priority.MEDIUM))
            assertNull(addError)
        }
        var (_,error) = manager.killGroup(Priority.LOW)
        assertEquals(manager.list().data?.size, 3)
        assertEquals(manager.list().data?.none { p -> p.priority == Priority.LOW }, true)
        assertNull(error)
    }
}