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

	// Whether this TaskWrapper consists of a pickup
	public boolean isPickup() {
		return this.pickup;
	}

	public City getEndCity() {
		City city;
		if (pickup) {
			// if the task is a pickup, vehicle ends up in the task's pickup city
			city = task.deliveryCity;
		} else {
			// else, vehicle ends up in the task's delivery city
			city = task.pickupCity;
		}
		return city;
	}

	@Override
	public boolean equals(Object that) {
		if (!(that instanceof TaskWrapper))
			return false;
		TaskWrapper taskWrapper = (TaskWrapper) that;
		return (this.task == taskWrapper.task) && (this.pickup == taskWrapper.pickup);
	}
}
