import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import logist.plan.Action;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;
import logist.plan.Action.Move;
import logist.plan.Action.Delivery;
import logist.plan.Action.Pickup;
import logist.plan.Plan;

public class SLS {
	private final long startTime;
	private long currentTime;
	private Solution solution;

	public SLS(List<Vehicle> vehicles, TaskSet tasks, long timeLimit) {
		this.startTime = System.currentTimeMillis();
		this.solution = selectInitialSolution(vehicles, tasks);
		this.currentTime = System.currentTimeMillis();
		

		//Discount the timelimit to ensure that a solution is returned
		timeLimit = timeLimit*(long)0.9;
				
		Solution solution = selectInitialSolution(vehicles, tasks);
		while (this.currentTime - this.startTime < timeLimit) { //TODO make sure this timing works for edge cases (what about stopping slightly before)
			Solution oldSolution = solution;
			ArrayList<Solution> neighbours = this.chooseNeighbours();
			solution = this.localChoice(neighbours);
			
			this.currentTime = System.currentTimeMillis();
		}
	}

	public Solution getSolution() {
		return this.solution;
	}
	
	/**
	 * Randomly choose a vehicle (A) out of the vehicles contained in solution.
	 * Choose another vehicle. Add the first Pickup of the vehicle (A) in front of the other vehicle. 
	 * Randomly choose places to put the corresponding delivery.
	 * Do this for all vehicles.
	 * @param solution
	 * @return a list of the generated solutions
	 */
	private ArrayList<Solution> swapFirstPickup(Solution solution) {
		//TODO Arthur
	}

	// TODO Simon
	private Solution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {

		/*
		 * Pseudo code outline here: Trying to be more efficient here. while
		 * TaskSet contains tasks For vehicle : vehicles: assign head of task
		 * list to vehicle Repeat: if another task exists at destination city of
		 * this task, add next task Break if no task exists at destination city
		 * end end
		 */

		// Not sure if TaskSet needs to be cloned? I think it does... else can
		// remove this, and re-reference below
		ArrayList<Task> workingTaskList = new ArrayList<Task>();
		// store tasks in a more workable format
		for (Task task : tasks) {
			workingTaskList.add(task);
		}

		//stores the action of the vehicle
		HashMap<Vehicle, ArrayList<Action>> vehicleAgendas = new HashMap<Vehicle, ArrayList<Action>>(); 
		
		//stores the location of the vehicle after the most recent action
		HashMap<Vehicle, City> vehicleCities = new HashMap<Vehicle, City>(); 
		while (!workingTaskList.isEmpty()) {
			for (Vehicle vehicle : vehicles) {
				// always ensure that there are tasks remaining in the list
				// before continuing
				if (workingTaskList.isEmpty()) {
					break;
				}

				// don't even need to worry about capacity because the package
				// is always dropped off first before picking up more!
				
				boolean canChain = true;
				int taskIndex = 0;
				while (canChain) {
					
					if (workingTaskList.isEmpty()) {
						break;
					}
					
					Task task = workingTaskList.get(taskIndex);

					if (vehicleAgendas.containsKey(vehicle)) {
						// add actions required to deliver task to agenda
						City currentCity = vehicleCities.get(vehicle);
						// if vehicle key already exists, append actions to
						// current list
						vehicleAgendas.get(vehicle).addAll(getDeliveryActions(currentCity, task));
					} else {
						// if key doesn't exist yet, initialize with a new
						// arrayList
						City currentCity = vehicle.homeCity();
						vehicleAgendas.put(vehicle, getDeliveryActions(currentCity, task));
					}

					vehicleCities.put(vehicle, task.deliveryCity);
					workingTaskList.remove(taskIndex);
					
					//see if there is a task that satisfies chaining here. If yes, keep canChain true
					canChain = false;
					for(Task testTask : workingTaskList){
						if(testTask.pickupCity == vehicleCities.get(vehicle)){
							canChain = true;
							taskIndex = workingTaskList.indexOf(testTask);
							break; //only take the first task satisfying the criteria
						}
					}					
				}
			}
		}		
		return new Solution(vehicleAgendas);
	}

	// TODO Simon
	private ArrayList<Solution> chooseNeighbours() {
		return null;
	}
	
	/**
	 * 
	 * @param solutions
	 * @return the first solution in *solutions* whose total cost is the lowest amongst the ones presented in *solutions*. 
	 * If *solutions* is empty, return null
	 */
	private Solution localChoice(ArrayList<Solution> solutions) {
		double optimalSolutionTotalCost = Double.POSITIVE_INFINITY;
		Solution optimalSolution = null;
		
		for (Solution solution : solutions) {
			double solutionTotalCost = solution.getTotalCost();
			if (solutionTotalCost < optimalSolutionTotalCost) {
				optimalSolution = solution;
				optimalSolutionTotalCost = solutionTotalCost;
			}
		}
		
		return optimalSolution;
	}

	private ArrayList<Action> getDeliveryActions(City origin, Task task) {

		ArrayList<Action> actions = new ArrayList<Action>();
		ArrayList<City> pathCities = (ArrayList<City>) origin.pathTo(task.deliveryCity);

		actions.add(new Pickup(task));

		for (City pathCity : pathCities) {
			actions.add(new Move(pathCity));
		}

		actions.add(new Delivery(task));
		return actions;
	}

}
