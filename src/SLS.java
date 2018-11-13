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
	/*
	 * List of the best solutions we've seen. The first element is the one with the
	 * lowest cost
	 */
	private SolutionList solutions;
	private final int amountBestSolutions; // How many "best solutions" we're keeping, i.e. the size of solutions
	private final double P_LOWER = 1;
	private final double P_UPPER = 1;

	private int repeatCount = 0;
	/*
	 * If the same local minimum is found MAX_REPEAT times in a row, a neighbor is
	 * randomly selected
	 */
	private final int MAX_REPEAT = 10;

	public SLS(List<Vehicle> vehicles, TaskSet tasks, long timeLimit, int amountBestSolutions) {
		this.startTime = System.currentTimeMillis();
		this.currentTime = System.currentTimeMillis();
		this.amountBestSolutions = amountBestSolutions;
		// Discount the time limit to ensure that a solution is returned
		timeLimit -= 500; // The other way didn't work somehow - non-integer
							// time maybe?

		// Initialize the solutions
		this.solutions = new SolutionList(amountBestSolutions);

		// Generate the first solution
		int generateInitial = 1;
		switch (generateInitial) {
		case 1:
			this.solutions.add(generateInitialSolutionNaive(vehicles, tasks));
			break;
		case 2:
			this.solutions.add(generateInitialSolutionGreedy(vehicles, tasks));
			break;
		}

		SolutionList localMins = this.solutions; // stores the solutions with the lowest cost we've encountered

		// This is the solution that will be used to generate neighbors
		Solution solutionGeneratingNeighbors = this.solutions.getFirstSolution();

		double diffTime = this.currentTime - this.startTime;
		System.out.println("The time limit is " + timeLimit);
		while (diffTime < timeLimit) {

			// Generate neighbors
			ArrayList<Solution> neighbors = this.generateNeighbours(solutionGeneratingNeighbors);
			neighbors.add(0, solutionGeneratingNeighbors); // current solution generating neighbors kept in case nothing
															// is better

			// Find this.amountBestSolutions neighbors with the lowest costs
			localMins = this.getLocalMin(neighbors, this.amountBestSolutions);
			
			/*
			 * If the solution within the neighbors with the lowest cost has a cost <=
			 * than the cost of the solution within this.solutions with the highest cost,
			 * then we need to merge localMins INTO this.solutions
			 */
			if (localMins.getFirstSolution().totalCost <= this.solutions.getLastSolution().totalCost) {
				// Merge localMins and this.solutions
				this.solutions.addAll(localMins.getAll());
				this.repeatCount = 0;
			} else {
				this.repeatCount++;
			}

			// Choose which solution to use to generate neighbors
			solutionGeneratingNeighbors = this.localChoice(neighbors, localMins.getFirstSolution(), solutionGeneratingNeighbors);

			diffTime = System.currentTimeMillis() - this.startTime;
		}
		
		System.out.print("Costs : ");
		for (Solution s : this.solutions.getAll()) {
			System.out.print(s.totalCost);
			System.out.print(" - ");
		}
		System.out.println();

	}

	public SolutionList getSolutions() {
		return this.solutions;
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
	private Solution generateInitialSolutionNaive(List<Vehicle> vehicles, TaskSet tasks) {

		ArrayList<Task> pickupTaskList = new ArrayList<Task>();
		ArrayList<Task> deliveryTaskList = new ArrayList<Task>();
		// store tasks in a more workable format
		for (Task task : tasks) {
			pickupTaskList.add(task);
		}

		// stores the action of the vehicle
		HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas = new HashMap<Vehicle, ArrayList<TaskWrapper>>();

		// INITIALIZE
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

	private Solution generateInitialSolutionGreedy(List<Vehicle> vehicles, TaskSet tasks) {

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
	private ArrayList<Solution> generateNeighbours(Solution oldSolution) {
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
	private Solution localChoice(ArrayList<Solution> neighbors, Solution localMin,
			Solution solutionGeneratingNeighbors) {
		Random rand = new Random();
		double x = rand.nextDouble();

		/*
		 * If the same local minimum is found MAX_REPEAT times in a row, a neighbor is
		 * randomly selected
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
				return solutionGeneratingNeighbors;
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
	 * @return return a SolutionList with the solutions in *solutions* with the lowest total cost
	 */
	private SolutionList getLocalMin(ArrayList<Solution> solutions, int amountOfLocalMin) {

		// Initialize
		SolutionList localMinSolutions = new SolutionList(amountOfLocalMin);
	
		// Find the solutions amongst *solutions* with the lowest total cost
		for (Solution s : solutions) {
			localMinSolutions.add(s);
		}
		return localMinSolutions;
	}

}
