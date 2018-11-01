import logist.plan.Action;
import logist.task.Task;
import logist.topology.Topology.City;
import logist.plan.Action.Pickup;
import logist.plan.Action.Delivery;

public class TaskWrapper {
	
	private Task task;
	private boolean pickup; // What we're doing with the task. Are we picking it up or delivering it ?
	private Action taskAction;
	
	public TaskWrapper(Task task, boolean pickup) {
		this.task = task;
		this.pickup = pickup;
		if (pickup) {
			this.taskAction = new Pickup(this.task);
		} else {
			this.taskAction = new Delivery(this.task);
		}
	}
	
	public Action getTaskAction() {
		return this.taskAction;
	}
	
	public Task getTask() {
		return this.task;
	}
	
	//Whether this TaskWrapper consists of a pickup 
	public boolean isPickup() {
		return this.pickup;
	}
}
