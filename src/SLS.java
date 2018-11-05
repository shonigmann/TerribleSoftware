import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class SLS {
	private final long startTime;
	private long currentTime;
	private Solution solution;

	private final double P_LOWER = 0.5;
	private final double P_UPPER = 0.9;

	public SLS(List<Vehicle> vehicles, TaskSet tasks, long timeLimit) {
		this.startTime = System.currentTimeMillis();
		this.solution = selectInitialSolution(vehicles, tasks);
		this.currentTime = System.currentTimeMillis();

		// Discount the time limit to ensure that a solution is returned
		timeLimit -= 500; // The other way didn't work somehow - non-integer
							// time maybe?

		int selectInitial = 2;
		Solution solution = null;

		switch (selectInitial) {
		case 1:
			solution = selectInitialSolution(vehicles, tasks);
			break;
		case 2:
			solution = selectInitialSolutionNaive(vehicles, tasks);
			break;
		case 3:
			solution = selectInitialSolutionGreedy(vehicles, tasks);
			break;
		case 4:
			solution = this.selectInitialSolutionFeedAllToFirst(vehicles, tasks);
			break;
		}

		Solution homeSolution = solution; // stores current solution used to
											// generate neighbors
		Solution localMin = solution; // stores the current local minimum

		double diffTime = this.currentTime - this.startTime;
		System.out.println("The time limit is " + timeLimit);
		while (diffTime < timeLimit) { // TODO make sure this timing works for
										// edge cases (what about stopping
										// slightly before)
			ArrayList<Solution> neighbors = this.chooseNeighbours(homeSolution);
			neighbors.add(0, homeSolution); // current solution kept in case
											// nothing is better
			localMin = this.getLocalMin(neighbors);// stores the local minimum
													// from the current set of
													// neighbors
			homeSolution = this.localChoice(neighbors, localMin, homeSolution);

			diffTime = System.currentTimeMillis() - this.startTime;
		}
		System.out.println("SLS constructed");
		
	}

	public Solution getSolution() {
		return this.solution;
	}

	/**
	 * Take the first task that the vehicle will handle, and swap its positions
	 * (pickup and delivery) with the positions of all the other tasks that this
	 * vehicle will handle. For each swap, create a new solution. The old solution
	 * is not in the generated bunch. If the vehicle only has one task, it cannot
	 * swap anything, and therefore returns an empty List.
	 * 
	 * @param vehicle
	 * @param simpleVehicleAgendas
	 * @return a list of solutions with the corresponding swap. Each solution is ONE
	 *         swap.
	 */
	public ArrayList<Solution> swapFirstTask(Vehicle vehicle,
			HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {

		assert simpleVehicleAgendas.containsKey(vehicle);
		ArrayList<Solution> solutions = new ArrayList<Solution>();

		// TODO: is there anything preventing this from swapping a task with
		// itself? ANSWER : Yes, the line "tasks.remove(0)"
		if (!simpleVehicleAgendas.get(vehicle).isEmpty()) {
			// Find all the tasks that vehicle handles
			ArrayList<Task> tasks = new ArrayList<Task>();
			for (TaskWrapper taskWrapper : simpleVehicleAgendas.get(vehicle)) {
				if (!tasks.contains(taskWrapper.getTask())) {
					tasks.add(taskWrapper.getTask());
				}
			}

			Task firstTask = tasks.get(0);
			tasks.remove(0);
			for (Task t : tasks) {
				solutions.add(this.swapTwoTasks(vehicle, firstTask, t, simpleVehicleAgendas));
			}
		}
		return solutions;
	}

	/**
	 * Swap the positions of the taskA (pickup and delivery) with the ones of the
	 * taskB (pickup and delivery) in the vehicle's agenda
	 * 
	 * @param vehicle
	 * @param taskA
	 * @param taskB
	 * @param simpleVehicleAgendas
	 * @return a Solution with the corresponding swap
	 */
	public Solution swapTwoTasks(Vehicle vehicle, Task taskA, Task taskB,
			HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {
		assert simpleVehicleAgendas.containsKey(vehicle);

		HashSet<Task> tasks = new HashSet<Task>();
		for (TaskWrapper taskWrapper : simpleVehicleAgendas.get(vehicle)) {
			tasks.add(taskWrapper.getTask());
		}

		assert tasks.contains(taskA) && tasks.contains(taskB);

		/* Swapping */
		@SuppressWarnings("unchecked")
		ArrayList<TaskWrapper> newSimpleVehicleAgenda = (ArrayList<TaskWrapper>) simpleVehicleAgendas.get(vehicle)
				.clone();

		int posPickupTaskA = -1;
		int posPickupTaskB = -1;
		int posDeliveryTaskA = -1;
		int posDeliveryTaskB = -1;

		// Finding where is the pickup and delivery of both tasks
		for (int i = 0; i < newSimpleVehicleAgenda.size(); i++) {
			TaskWrapper tw = newSimpleVehicleAgenda.get(i);
			if (tw.getTask() == taskA) {
				if (tw.isPickup()) {
					posPickupTaskA = i;
				} else {
					posDeliveryTaskA = i;
				}
			} else if (tw.getTask() == taskB) {
				if (tw.isPickup()) {
					posPickupTaskB = i;
				} else {
					posDeliveryTaskB = i;
				}
			}
		}

		// Swap
		TaskWrapper pickupTaskB = newSimpleVehicleAgenda.get(posPickupTaskB);
		TaskWrapper deliveryTaskB = newSimpleVehicleAgenda.get(posDeliveryTaskB);

		TaskWrapper pickupTaskA = newSimpleVehicleAgenda.get(posPickupTaskA);
		TaskWrapper deliveryTaskA = newSimpleVehicleAgenda.get(posDeliveryTaskA);

		// Put task A at taskB's place
		newSimpleVehicleAgenda.set(posPickupTaskB, pickupTaskA);
		newSimpleVehicleAgenda.set(posDeliveryTaskB, deliveryTaskA);

		// Put task B at taskA's place
		newSimpleVehicleAgenda.set(posPickupTaskA, pickupTaskB);
		newSimpleVehicleAgenda.set(posDeliveryTaskA, deliveryTaskB);

		simpleVehicleAgendas.put(vehicle, newSimpleVehicleAgenda);

		return new Solution(simpleVehicleAgendas);
	}

	/**
	 * Transfer the first task that choseVehicle will handle to all the other
	 * vehicles. The pickup part of the task is put at the front of the other
	 * vehicle's agenda. Additionally, the delivery part of the task is put at
	 * every places in can (directly after the pickup, then slightly after, and
	 * so on) Each transfer corresponds to one solution. 
	 * A transfer is not done if the vehicle doesn't have the capacity to handle the newly transfered task.
	 * The old solution is NOT in the generated bunch.
	 * 
	 * @param chosenVehicle
	 * @param simpleVehicleAgendas
	 * @return a list with all the solutions newly generated
	 */
	public ArrayList<Solution> transferFirstTask(Vehicle chosenVehicle, HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {

		ArrayList<Solution> solutions = new ArrayList<Solution>();

		// Make sure that chosenVehicle is in simpleVehicleAgendas
		assert simpleVehicleAgendas.containsKey(chosenVehicle);

		if (!simpleVehicleAgendas.get(chosenVehicle).isEmpty()) {
			ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();
			vehicles.addAll(simpleVehicleAgendas.keySet());
			vehicles.remove(chosenVehicle);

			// Generate the new simpleVehicleAgenda of the chosenVehicle (after
			// the transfer)
			@SuppressWarnings("unchecked")
			ArrayList<TaskWrapper> simpleChosenVehicleAgenda = (ArrayList<TaskWrapper>) simpleVehicleAgendas.get(chosenVehicle).clone();

			TaskWrapper pickupToTransfer = simpleChosenVehicleAgenda.get(0); // The
																				// first
																				// TaskWrapper
																				// in
																				// the
																				// chosenVehicle's
																				// agenda

			// Check
			assert pickupToTransfer.isPickup();

			simpleChosenVehicleAgenda.remove(0); // Remove the pickup

			TaskWrapper deliveryToTransfer = new TaskWrapper(pickupToTransfer.getTask(), false);
			simpleChosenVehicleAgenda.remove(deliveryToTransfer);

			// make sure we got rid of the pickup and delivery
			assert simpleChosenVehicleAgenda.size() - 2 == simpleVehicleAgendas.get(chosenVehicle).size();

			for (Vehicle vehicle : vehicles) {

				@SuppressWarnings("unchecked")
				ArrayList<TaskWrapper> tempSimpleVehicleAgenda = (ArrayList<TaskWrapper>) simpleVehicleAgendas.get(vehicle).clone();
				tempSimpleVehicleAgenda.add(0, pickupToTransfer);

				
				// Create one solution per place where you can append the
				// delivery
				for (int i = 1; i <= tempSimpleVehicleAgenda.size(); i++) {
					// handle the chosenVehicle
					@SuppressWarnings("unchecked")
					HashMap<Vehicle, ArrayList<TaskWrapper>> newSimpleVehicleAgendas = (HashMap<Vehicle, ArrayList<TaskWrapper>>) simpleVehicleAgendas.clone();
					newSimpleVehicleAgendas.put(chosenVehicle, simpleChosenVehicleAgenda);

					// handle the current vehicle
					@SuppressWarnings("unchecked")
					ArrayList<TaskWrapper> simpleVehicleAgenda = (ArrayList<TaskWrapper>) tempSimpleVehicleAgenda.clone();
					simpleVehicleAgenda.add(i, deliveryToTransfer);

					// check that the newly created simpleVehicleAgenda doesn't have the vehicle carry more than its capacity
					if (this.canBeCarried(vehicle, simpleVehicleAgenda)) {
						newSimpleVehicleAgendas.put(vehicle, simpleVehicleAgenda);

						// create new solution based on the new agendas
						solutions.add(new Solution(newSimpleVehicleAgendas));
					}
					
				}
			}
		}

		return solutions;
	}
	
	/**
	 * Whether or not the vehicle can carry out the vehicleAgenda (with respect to its capacity).
	 * i.e. whether at any point during the vehicleAgenda, the vehicle has to carry strictly more than its capacity
	 * @param vehicle
	 * @param vehicleAgenda
	 * @return true if the vehicleAgenda can be carried out by the vehicle, false otherwise
	 */
	private boolean canBeCarried(Vehicle vehicle, ArrayList<TaskWrapper>  vehicleAgenda) {
		/*
		 *  Generate a list with how much the vehicle is carrying at each step
		 *  The 0th element of that list is how much weight the vehicle carries AFTER having done the 0th action on vehicleAgenda
		 */
		ArrayList<Double> totalCarriedWeights = new ArrayList<Double>();
		double lastTotalCarriedWeight = 0.0;
		for (TaskWrapper tw : vehicleAgenda) {
			double currentTotalCarriedWeight = lastTotalCarriedWeight;
			
			// Add or remove the weight depending on if we're picking up or delivering the task
			if (tw.isPickup()) {
				currentTotalCarriedWeight += tw.getTask().weight;
			} else {
				currentTotalCarriedWeight -= tw.getTask().weight;
			}
			
			totalCarriedWeights.add(currentTotalCarriedWeight);
			lastTotalCarriedWeight = currentTotalCarriedWeight;
		}
		
		// Find the max of that list
		double maxCarriedWeight = Collections.max(totalCarriedWeights);
		
		return maxCarriedWeight <= vehicle.capacity();
	}
	
	/**
	 * Create a solution where the first vehicle will pick up and delivery each task, one by one
	 * If at one point the first vehicle cannot carry a task, it'll try to give it to the next one, and so on.
	 * 
	 * @param vehicles
	 * @param tasks
	 * @return
	 */
	private Solution selectInitialSolutionFeedAllToFirst(List<Vehicle> vehicles, TaskSet tasks) { 
		HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas = new HashMap<Vehicle, ArrayList<TaskWrapper>>();
		
		// Initializing simpleVehicleAgendas
		for (Vehicle v : vehicles) {
			simpleVehicleAgendas.put(v, new ArrayList<TaskWrapper>());
		}
		
		Vehicle vehicle = null;
		for (Task task : tasks) {
			// Find the first vehicle that can carry the task
			for (Vehicle v : vehicles) {
				if (v.capacity() >= task.weight) {
					vehicle = v;
					break;
				}
			}
			
			assert vehicle != null; //If the vehicle is null at this point, it means there is no vehicle able to carry the task
			
			simpleVehicleAgendas.get(vehicle).add(new TaskWrapper(task, true));
			simpleVehicleAgendas.get(vehicle).add(new TaskWrapper(task, false));
		}
		
		return new Solution(simpleVehicleAgendas);
	}
		

	/*
	 * Just assign tasks to vehicles one by one, no thinking about it
	 */
	private Solution selectInitialSolutionNaive(List<Vehicle> vehicles, TaskSet tasks) {

		ArrayList<Task> pickupTaskList = new ArrayList<Task>();
		ArrayList<Task> deliveryTaskList = new ArrayList<Task>();
		// store tasks in a more workable format
		for (Task task : tasks) {
			pickupTaskList.add(task);
		}

		// stores the action of the vehicle
		HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas = new HashMap<Vehicle, ArrayList<TaskWrapper>>();

		while (!pickupTaskList.isEmpty()) {

			for (Vehicle vehicle : vehicles) {
				if (pickupTaskList.isEmpty()) {
					break;
				}

				Task task = pickupTaskList.get(0);
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
				pickupTaskList.remove(0);
			}
		}

		return new Solution(simpleVehicleAgendas);
	}

	/*
	 * Chain tasks together by assigning single tasks and
	 */
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

					// see if there is a task that satisfies chaining here. If
					// yes, keep canChain
					// true
					canChain = false;
					for (Task testTask : workingTaskList) {
						if (testTask.pickupCity == vehicleCities.get(vehicle)) {
							canChain = true;
							taskIndex = workingTaskList.indexOf(testTask);
							break; // only take the first task satisfying the
									// criteria
						}
					}
				}
			}
		}
		return new Solution(vehicles, simpleVehicleAgendas);
	}

	private Solution selectInitialSolutionGreedy(List<Vehicle> vehicles, TaskSet tasks) {

		ArrayList<Task> pickupTaskList = new ArrayList<Task>();
		ArrayList<Task> deliveryTaskList = new ArrayList<Task>();
		// store tasks in a more workable format
		for (Task task : tasks) {
			pickupTaskList.add(task);
		}

		// stores the action of the vehicle
		HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas = new HashMap<Vehicle, ArrayList<TaskWrapper>>();

		while (!pickupTaskList.isEmpty()) {

			Task task = pickupTaskList.get(0);
			ArrayList<TaskWrapper> taskWrappers = new ArrayList<TaskWrapper>();
			taskWrappers.add(new TaskWrapper(task, true));// pickup
			taskWrappers.add(new TaskWrapper(task, false));// delivery

			if (simpleVehicleAgendas.containsKey(vehicles.get(0))) {
				// if vehicle key already exists, append actions to
				// current list
				simpleVehicleAgendas.get(vehicles.get(0)).addAll(taskWrappers);

			} else {
				// if key doesn't exist yet, initialize with a new
				// arrayList
				simpleVehicleAgendas.put(vehicles.get(0), taskWrappers);
			}
			pickupTaskList.remove(0);
		}

		// initialize other vehicles in solution with empty array lists
		for (int i = 1; i < vehicles.size(); i++) {
			simpleVehicleAgendas.put(vehicles.get(i), new ArrayList<TaskWrapper>());
		}

		return new Solution(simpleVehicleAgendas);
	}

	@SuppressWarnings("unchecked")
	private ArrayList<Solution> chooseNeighbours(Solution oldSolution) {
		// TODO BETTER RANDOM VEHICLE PICKED
		ArrayList<Solution> solutions = new ArrayList<Solution>();
		ArrayList<Vehicle> vehicles = (ArrayList<Vehicle>) oldSolution.getVehicles().clone();
		Collections.shuffle(vehicles);

		// BIG TODO: for some reason when a greedy initial solution is selected (all
		// tasks given sequentially to vehicle 1, there are often no solutions
		// generated...
		// this is despite me adding a loop to guarantee that swaps are made for all
		// vehicles...
		
		//IDEA 1
//		ArrayList<Solution> vehicleSolutions = new ArrayList<Solution>();
//		for (int i = 1; i < vehicles.size(); i++) {
//			Vehicle chosenVehicle = vehicles.get(i);
//			vehicleSolutions.addAll(this.transferFirstTask(chosenVehicle, oldSolution.getSimpleVehicleAgendas()));
//
//			ArrayList<Solution> vehicleSolutions2 = new ArrayList<Solution>();
//			for (Solution s : (ArrayList<Solution>) vehicleSolutions.clone()) {
//				vehicleSolutions2.addAll(this.swapFirstTask(chosenVehicle, s.getSimpleVehicleAgendas())); // TODO
//			}
//			solutions.addAll(vehicleSolutions2);
//
//			vehicleSolutions.addAll(this.swapFirstTask(chosenVehicle, oldSolution.getSimpleVehicleAgendas()));
//		}
//		solutions.addAll(vehicleSolutions);
		//! IDEA 1
		
		//IDEA 2
		
		// Remove all the vehicles with no tasks 
		for (Vehicle v : (ArrayList<Vehicle>) vehicles.clone()) {
			if (oldSolution.getSimpleVehicleAgendas().get(v).isEmpty()) {
				vehicles.remove(v);
			}
		}
		
		
		
		Collections.shuffle(vehicles);
		solutions.addAll(this.transferFirstTask(vehicles.get(0), oldSolution.getSimpleVehicleAgendas()));
		
		//for (int i = 1; i < vehicles.size(); i++) {
			Vehicle chosenVehicle = vehicles.get(0);
	
			for (Solution s : (ArrayList<Solution>) solutions.clone()) {
				solutions.addAll(this.swapFirstTask(chosenVehicle, s.getSimpleVehicleAgendas())); // TODO
			}
			
			solutions.addAll(this.swapFirstTask(chosenVehicle, oldSolution.getSimpleVehicleAgendas()));

		//}
		//!IDEA 2
		

		return solutions;
	}

	/**
	 * 
	 * @param solutions
	 * @return the stochastic choice for a solution. Depending on random number
	 *         generated, can be either the current solution, the optimal from the
	 *         set of neighbors, or a random solution from the set of neighbors
	 */
	private Solution localChoice(ArrayList<Solution> neighbors, Solution localMin, Solution homeSolution) {
		Random rand = new Random();
		double x = rand.nextDouble();

		// if random number is less than P_Lower, choose local min from
		// neighbors
		if (x < P_LOWER) {
			return localMin;
		}
		// if random number is between P_lower and P_upper, return the old
		// solution
		else if (x < P_UPPER) {
			return homeSolution;
		}
		// if the random number is above P_upper, return a random neighbor
		else {
			int y = rand.nextInt(neighbors.size());
			return neighbors.get(y);
		}

	}

	/**
	 * 
	 * @param solutions
	 * @return the first solution in *solutions* whose total cost is the lowest
	 *         amongst the ones presented in *solutions*. If *solutions* is empty,
	 *         return null
	 */
	private Solution getLocalMin(ArrayList<Solution> solutions) {
		double localMinSolutionTotalCost = Double.POSITIVE_INFINITY;
		Solution localMinSolution = null;

		for (Solution solution : solutions) {
			double solutionTotalCost = solution.totalCost;
			if (solutionTotalCost < localMinSolutionTotalCost) {
				localMinSolution = solution;
				localMinSolutionTotalCost = solutionTotalCost;
			}
		}

		if (localMinSolution.totalCost < this.solution.totalCost) {
			solution = localMinSolution; // store optimal solution if local min
											// trumps current optimal. Only
											// return local min.
		}
		System.out.println(
				"BEST SOLUTION COST: " + this.solution.totalCost + "; LOCAL CHOICE COSTS : " + localMinSolutionTotalCost
						+ "; Chosen from " + Integer.toString(solutions.size()) + " possible neighbors");

		return localMinSolution;
	}

}
