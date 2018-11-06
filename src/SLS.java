import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class SLS {
	private final long startTime;
	private long currentTime;
	private Solution solution; // currently best solution we've seen

	private final double P_LOWER = 1;
	private final double P_UPPER = 1; 

	private int repeatCount = 0;
	/* 
	 * If the same local minimum is found MAX_REPEAT times in a row,
	 * a neighbor is randomly selected
	 */
	private final int MAX_REPEAT = 10; 

	public SLS(List<Vehicle> vehicles, TaskSet tasks, long timeLimit) {
		this.startTime = System.currentTimeMillis();
		this.currentTime = System.currentTimeMillis();

		// Discount the time limit to ensure that a solution is returned
		timeLimit -= 500; // The other way didn't work somehow - non-integer
							// time maybe?

		int selectInitial = 1;

		switch (selectInitial) {
		case 1:
			this.solution = selectInitialSolutionNaive(vehicles, tasks);
			break;
		case 2:
			this.solution = selectInitialSolutionGreedy(vehicles, tasks);
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
	 * Transfer the Task t from v1 to v2. The new position of t in v2 will be
	 * newPickupPos and newDeliveryPos. If two newPickupPos and newDeliveryPos are
	 * too big, they will be at the very end of v2's agenda. If v2 cannot handle the
	 * new task, an empty list is returned
	 * 
	 * @param v1
	 * @param v2
	 * @param t
	 * @param newPickupPos
	 * @param newDeliveryPos
	 * @param simpleVehicleAgendas
	 * @return a list of solutions with ONE solution
	 */
	public ArrayList<Solution> transferTask(Vehicle v1, Vehicle v2, Task t, int newPickupPos, int newDeliveryPos,
			HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {
		assert newPickupPos < newDeliveryPos;
		assert simpleVehicleAgendas.get(v1).contains(new TaskWrapper(t, true)); // v1
																				// is
																				// in
																				// charge
																				// of
																				// the
																				// pickup
																				// of
																				// t
		assert simpleVehicleAgendas.get(v1).contains(new TaskWrapper(t, false)); // v1
																					// is
																					// in
																					// charge
																					// of
																					// the
																					// delivery
																					// of
																					// t

		ArrayList<Solution> solutions = new ArrayList<Solution>();

		// The new Agendas
		HashMap<Vehicle, ArrayList<TaskWrapper>> newSimpleVehicleAgendas = (HashMap<Vehicle, ArrayList<TaskWrapper>>) simpleVehicleAgendas
				.clone();

		// Creation of v1's simple agenda AFTER the transfer
		ArrayList<TaskWrapper> v1Agenda = (ArrayList<TaskWrapper>) newSimpleVehicleAgendas.get(v1).clone();
		v1Agenda.remove(new TaskWrapper(t, true));
		v1Agenda.remove(new TaskWrapper(t, false));

		// Creation of v2's simple agenda AFTER the transfer
		ArrayList<TaskWrapper> v2Agenda = (ArrayList<TaskWrapper>) newSimpleVehicleAgendas.get(v2).clone();
		if (v1 == v2) {
			v2Agenda = (ArrayList<TaskWrapper>) v1Agenda.clone();
		}

		// Adding of the pickup of task t
		if (newPickupPos < v2Agenda.size()) {
			v2Agenda.add(newPickupPos, new TaskWrapper(t, true));
		} else {
			v2Agenda.add(new TaskWrapper(t, true));
		}

		// Adding of the delivery of task t
		if (newDeliveryPos < v2Agenda.size()) {
			v2Agenda.add(newDeliveryPos, new TaskWrapper(t, false));
		} else {
			v2Agenda.add(new TaskWrapper(t, false));
		}

		// Make sure that v2 can carry its new Agenda
		if (this.canBeCarried(v2, v2Agenda)) {
			newSimpleVehicleAgendas.put(v1, v1Agenda);

			newSimpleVehicleAgendas.put(v2, v2Agenda);
			solutions.add(new Solution(newSimpleVehicleAgendas));
		}

		return solutions;
	}

	/**
	 * For every task handled by v1, transfer it to EVERY other vehicle. Put the
	 * pickup and delivery in ANY place allowed. Check for carriability
	 * 
	 * @param s
	 * @return
	 */
	public ArrayList<Solution> transferAllTasksToAllVehicles(Vehicle v1,
			HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {
		ArrayList<Solution> solutions = new ArrayList<Solution>();

		// Compute all the tasks handled by v1
		ArrayList<Task> tasks = new ArrayList<Task>();
		for (TaskWrapper tw : simpleVehicleAgendas.get(v1)) {
			if (tw.isPickup()) {
				tasks.add(tw.getTask());
			}
		}

		// List all the vehicles, but v1
		ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();
		for (Vehicle v : simpleVehicleAgendas.keySet()) {
			if (v != v1) {
				vehicles.add(v);
			}
		}

		for (Task t : tasks) {
			for (Vehicle v2 : simpleVehicleAgendas.keySet()) {
				int maxPickup = simpleVehicleAgendas.get(v2).size();
				for (int pickup = 0; pickup <= maxPickup; pickup++) {
					for (int delivery = pickup + 1; delivery <= maxPickup + 1; delivery++) {
						solutions.addAll(this.transferTask(v1, v2, t, pickup, delivery, simpleVehicleAgendas));
					}
				}
			}
		}

		return solutions;
	}

	/**
	 * Whether or not the vehicle can carry out the vehicleAgenda (with respect to
	 * its capacity). i.e. whether at any point during the vehicleAgenda, the
	 * vehicle has to carry strictly more than its capacity
	 * 
	 * @param vehicle
	 * @param vehicleAgenda
	 * @return true if the vehicleAgenda can be carried out by the vehicle, false
	 *         otherwise
	 */
	private boolean canBeCarried(Vehicle vehicle, ArrayList<TaskWrapper> vehicleAgenda) {
		/*
		 * Generate a list with how much the vehicle is carrying at each step The 0th
		 * element of that list is how much weight the vehicle carries AFTER having done
		 * the 0th action on vehicleAgenda
		 */
		ArrayList<Double> totalCarriedWeights = new ArrayList<Double>();
		double lastTotalCarriedWeight = 0.0;
		for (TaskWrapper tw : vehicleAgenda) {
			double currentTotalCarriedWeight = lastTotalCarriedWeight;

			// Add or remove the weight depending on if we're picking up or
			// delivering the
			// task
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

		//INITIALIZE 
		for (Vehicle v : vehicles) {
			simpleVehicleAgendas.put(v, new ArrayList<TaskWrapper>());
		}
		
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

	
//	private Solution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
//
//		/*
//		 * Pseudo code outline here: Trying to be more efficient here. while TaskSet
//		 * contains tasks For vehicle : vehicles: assign head of task list to vehicle
//		 * Repeat: if another task exists at destination city of this task, add next
//		 * task Break if no task exists at destination city end end
//		 */
//
//		ArrayList<Task> workingTaskList = new ArrayList<Task>();
//		// store tasks in a more workable format
//		for (Task task : tasks) {
//			workingTaskList.add(task);
//		}
//
//		// stores the action of the vehicle
//		HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas = new HashMap<Vehicle, ArrayList<TaskWrapper>>();
//
//		// stores the location of the vehicle after the most recent action
//		HashMap<Vehicle, City> vehicleCities = new HashMap<Vehicle, City>();
//		while (!workingTaskList.isEmpty()) {
//			for (Vehicle vehicle : vehicles) {
//				// always ensure that there are tasks remaining in the list
//				// before continuing
//				if (workingTaskList.isEmpty()) {
//					break;
//				}
//
//				// don't even need to worry about capacity because the package
//				// is always dropped off first before picking up more!
//
//				boolean canChain = true;
//				int taskIndex = 0;
//				while (canChain) {
//
//					if (workingTaskList.isEmpty()) {
//						break;
//					}
//
//					Task task = workingTaskList.get(taskIndex);
//
//					ArrayList<TaskWrapper> taskWrappers = new ArrayList<TaskWrapper>();
//					taskWrappers.add(new TaskWrapper(task, true));// pickup
//					taskWrappers.add(new TaskWrapper(task, false));// delivery
//
//					if (simpleVehicleAgendas.containsKey(vehicle)) {
//						// if vehicle key already exists, append actions to
//						// current list
//						simpleVehicleAgendas.get(vehicle).addAll(taskWrappers);
//
//					} else {
//						// if key doesn't exist yet, initialize with a new
//						// arrayList
//						simpleVehicleAgendas.put(vehicle, taskWrappers);
//					}
//
//					vehicleCities.put(vehicle, task.deliveryCity);
//					workingTaskList.remove(taskIndex);
//
//					// see if there is a task that satisfies chaining here. If
//					// yes, keep canChain
//					// true
//					canChain = false;
//					for (Task testTask : workingTaskList) {
//						if (testTask.pickupCity == vehicleCities.get(vehicle)) {
//							canChain = true;
//							taskIndex = workingTaskList.indexOf(testTask);
//							break; // only take the first task satisfying the
//									// criteria
//						}
//					}
//				}
//			}
//		}
//		return new Solution(vehicles, simpleVehicleAgendas);
//	}

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

			// Find the first vehicle who can carry the task
			Vehicle vehicle = null;
			for (Vehicle v : vehicles) {
				if (v.capacity() >= task.weight) {
					vehicle = v;
					break;
				}
			}
			
			if (vehicle == null) {
				throw new IllegalArgumentException("There is a task that cannot be carried by any of the vehicle.");
			}

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

		// initialize other vehicles in solution with empty array lists
		for (int i = 1; i < vehicles.size(); i++) {
			simpleVehicleAgendas.put(vehicles.get(i), new ArrayList<TaskWrapper>());
		}

		return new Solution(simpleVehicleAgendas);
	}

	@SuppressWarnings("unchecked")
	private ArrayList<Solution> chooseNeighbours(Solution oldSolution) {
		ArrayList<Solution> solutions = new ArrayList<Solution>();
		ArrayList<Vehicle> vehicles = (ArrayList<Vehicle>) oldSolution.getVehicles().clone();

		// Remove all the vehicles with no tasks
		for (Vehicle v : (ArrayList<Vehicle>) vehicles.clone()) {
			if (oldSolution.getSimpleVehicleAgendas().get(v).isEmpty()) {
				vehicles.remove(v);
			}
		}
		
		Random rand = new Random();
		Vehicle chosenVehicle = vehicles.get(rand.nextInt(vehicles.size()));

		solutions.addAll(this.transferAllTasksToAllVehicles(chosenVehicle, oldSolution.getSimpleVehicleAgendas()));
		
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

		/* 
		 * If the same local minimum is found MAX_REPEAT times in a row,
		 * a neighbor is randomly selected
		 */
		if (repeatCount >= this.MAX_REPEAT) {
			int y = rand.nextInt(neighbors.size());
			this.repeatCount = 0;
			return neighbors.get(y);
		} else {
			// if random number is less than P_Lower, choose local min from
			// neighbors
			if (x < P_LOWER) {
				return localMin;
			}
			// if random number is between P_lower and P_upper, return the old
			// solution
			else if (x <= P_UPPER) {
				return homeSolution;
			}
			// if the random number is above P_upper, return a random neighbor
			else {
				int y = rand.nextInt(neighbors.size());
				return neighbors.get(y);
			}
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
			this.repeatCount = 0;
		} else {
			this.repeatCount++;
		}
		//used for debugging
		// System.out.println("BEST SOLUTION COST: " + this.solution.totalCost + "; LOCAL CHOICE COSTS : " + localMinSolutionTotalCost+ "; Chosen from " + Integer.toString(solutions.size()) + " possible neighbors");

		return localMinSolution;
	}

}
