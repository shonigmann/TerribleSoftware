import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class SLS {
	private final long startTime;
	private long currentTime;
	private Solution solution;

	public SLS(List<Vehicle> vehicles, TaskSet tasks, long timeLimit) {
		this.startTime = System.currentTimeMillis();
		this.solution = selectInitialSolution(vehicles, tasks);
		this.currentTime = System.currentTimeMillis();

		// Discount the time limit to ensure that a solution is returned
		timeLimit -= 500; // The other way didn't work somehow

		Solution solution = selectInitialSolution(vehicles, tasks);
		double diffTime = this.currentTime - this.startTime;
		System.out.println("The time limit is " + timeLimit);
		while (diffTime < timeLimit) { // TODO make sure this timing works for edge cases (what
																// about stopping slightly before)
			Solution oldSolution = solution;
			ArrayList<Solution> neighbors = this.chooseNeighbours(oldSolution);
			solution = this.localChoice(neighbors);
			
			diffTime =  System.currentTimeMillis() - this.startTime;
		}
		System.out.println("SLS constructed");
	}

	public Solution getSolution() {
		return this.solution;
	}

	/**
	 * @param chosenVehicle
	 * @param simpleVehicleAgendas
	 * @return
	 */
	public ArrayList<Solution> transferFirstTask(Vehicle chosenVehicle,
			HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {

		ArrayList<Solution> solutions = new ArrayList<Solution>();
		solutions.add(new Solution(simpleVehicleAgendas)); // Add the old solution, so that we avoid bad choices

		
		// Make sure that chosenVehicle is in simpleVehicleAgendas
		assert simpleVehicleAgendas.containsKey(chosenVehicle);

		if (!simpleVehicleAgendas.get(chosenVehicle).isEmpty()) {
			ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();
			vehicles.addAll(simpleVehicleAgendas.keySet());
			vehicles.remove(chosenVehicle);

			// Generate the new simpleVehicleAgenda of the chosenVehicle (after the transer)
			ArrayList<TaskWrapper> simpleChosenVehicleAgenda = (ArrayList<TaskWrapper>) simpleVehicleAgendas
					.get(chosenVehicle).clone();

			TaskWrapper pickupToTransfer = simpleChosenVehicleAgenda.get(0); // The first TaskWrapper in the chosenVehicle's
																				// agenda

			// Check
			assert pickupToTransfer.isPickup();

			simpleChosenVehicleAgenda.remove(0); // Remove the pickup

			TaskWrapper deliveryToTransfer = new TaskWrapper(pickupToTransfer.getTask(), false);
			simpleChosenVehicleAgenda.remove(deliveryToTransfer);

			// make sure we got rid of the pickup and delivery
			assert simpleChosenVehicleAgenda.size() - 2 == simpleVehicleAgendas.get(chosenVehicle).size();

			for (Vehicle vehicle : vehicles) {

				ArrayList<TaskWrapper> tempSimpleVehicleAgenda = (ArrayList<TaskWrapper>) simpleVehicleAgendas.get(vehicle)
						.clone();
				tempSimpleVehicleAgenda.add(0, pickupToTransfer);

				// Create one solution per place where you can append the delivery
				for (int i = 1; i < tempSimpleVehicleAgenda.size(); i++) {
					// handle the chosenVehicle
					HashMap<Vehicle, ArrayList<TaskWrapper>> newSimpleVehicleAgendas = (HashMap<Vehicle, ArrayList<TaskWrapper>>) simpleVehicleAgendas
							.clone();
					newSimpleVehicleAgendas.put(chosenVehicle, simpleChosenVehicleAgenda);

					// handle the current vehicle
					ArrayList<TaskWrapper> simpleVehicleAgenda = (ArrayList<TaskWrapper>) tempSimpleVehicleAgenda.clone();
					simpleVehicleAgenda.add(i, deliveryToTransfer);
					newSimpleVehicleAgendas.put(vehicle, simpleVehicleAgenda);

					// create new solution based on the new agendas
					solutions.add(new Solution(newSimpleVehicleAgendas));
				}
			}
		}
		
		return solutions;
	}

	// TODO Simon
	private Solution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {

		/*
		 * Pseudo code outline here: Trying to be more efficient here. while TaskSet
		 * contains tasks For vehicle : vehicles: assign head of task list to vehicle
		 * Repeat: if another task exists at destination city of this task, add next
		 * task Break if no task exists at destination city end end
		 */

		// Not sure if TaskSet needs to be cloned? I think it does... else can
		// remove this, and re-reference below
		ArrayList<Task> workingTaskList = new ArrayList<Task>();
		// store tasks in a more workable format
		for (Task task : tasks) {
			workingTaskList.add(task);
		}

		// stores the action of the vehicle
		HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas = new HashMap<Vehicle, ArrayList<TaskWrapper>>();

		// stores the location of the vehicle after the most recent action
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

					ArrayList<TaskWrapper> taskWrappers = new ArrayList<TaskWrapper>();
					taskWrappers.add(new TaskWrapper(task, true));// pickup
					taskWrappers.add(new TaskWrapper(task, false));// delivery

					if (simpleVehicleAgendas.containsKey(vehicle)) {
						// if vehicle key already exists, append actions to
						// current list
						simpleVehicleAgendas.get(vehicle).addAll(taskWrappers);

					} else {
						// if key doesn't exist yet, initialize with a new
						// arrayList
						simpleVehicleAgendas.put(vehicle, taskWrappers);
					}

					vehicleCities.put(vehicle, task.deliveryCity);
					workingTaskList.remove(taskIndex);

					// see if there is a task that satisfies chaining here. If yes, keep canChain
					// true
					canChain = false;
					for (Task testTask : workingTaskList) {
						if (testTask.pickupCity == vehicleCities.get(vehicle)) {
							canChain = true;
							taskIndex = workingTaskList.indexOf(testTask);
							break; // only take the first task satisfying the criteria
						}
					}
				}
			}
		}
		return new Solution(vehicles, simpleVehicleAgendas);
	}

	// TODO Simon
	private ArrayList<Solution> chooseNeighbours(Solution oldSolution) {
		// TODO BETTER RANDOM VEHICLE PICKED
		ArrayList<Solution> solutions = new ArrayList<Solution>();
		ArrayList<Vehicle> vehicles = oldSolution.getVehicles(); 
		//Collections.shuffle(vehicles);
		Vehicle chosenVehicle =  vehicles.get(0);
		
		solutions.addAll(
				this.transferFirstTask(chosenVehicle, oldSolution.getSimpleVehicleAgendas()));
		return solutions;
	}

	/**
	 * 
	 * @param solutions
	 * @return the first solution in *solutions* whose total cost is the lowest
	 *         amongst the ones presented in *solutions*. If *solutions* is empty,
	 *         return null
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
		
		System.out.println("LOCAL CHOICE COSTS : " + optimalSolutionTotalCost);

		return optimalSolution;
	}

}
