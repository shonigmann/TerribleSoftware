import java.util.ArrayList;
import java.util.HashMap;

import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Plan;
import logist.simulation.Vehicle;

public class Solution {
	private HashMap<Vehicle, ArrayList<Action>> vehicleAgendas;
	
	public Solution(HashMap<Vehicle, ArrayList<Action>> vehicleAgendas) {
		this.vehicleAgendas = vehicleAgendas;
	}
	
	//TODO make methods for handy neighboring solution generation
	
	/**
	 * 
	 * @return the total cost of this solution
	 */
	public double getTotalCost() {
		double totalCostOfThisSolution = 0;
		for (Vehicle vehicle : this.vehicleAgendas.keySet()) {
			Plan planOfThisVehicle = new Plan(vehicle.getCurrentCity(), this.vehicleAgendas.get(vehicle));
			double totalCostOfThisVehicle = planOfThisVehicle.totalDistance() * vehicle.costPerKm();
			totalCostOfThisSolution += totalCostOfThisVehicle;
		}
		return totalCostOfThisSolution;
	}
}
