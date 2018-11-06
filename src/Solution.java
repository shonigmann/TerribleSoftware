import java.util.ArrayList;
import java.util.HashMap;

import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Action.Delivery;
import logist.simulation.Vehicle;
import logist.topology.Topology.City;

public class Solution {
	private HashMap<Vehicle, ArrayList<Action>> vehicleAgendas;
	private HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas;

	public Solution(HashMap<Vehicle, ArrayList<TaskWrapper>> simpleVehicleAgendas) {
		this.simpleVehicleAgendas = simpleVehicleAgendas;

		// Compute vehicleAgendas
		for (Vehicle v : simpleVehicleAgendas.keySet()) {
			ArrayList<Action> vAgenda = new ArrayList<Action>();

			City fromCity = v.getCurrentCity();

			for (TaskWrapper tw : simpleVehicleAgendas.get(v)) {
				// Add all the actions necessary to where the taskWrapper is happening
				City toCity = tw.getEndCity();
				for (City c : fromCity.pathTo(toCity)) {
					vAgenda.add(new Move(toCity));
					fromCity = toCity;
				}

				// Add the action of the taskWrapper
				if (tw.isPickup()) {
					vAgenda.add(new Pickup(tw.getTask()));
				} else {
					vAgenda.add(new Delivery(tw.getTask()));
				}
			}
		}
	}

	/**
	 * @return the total cost of this solution
	 */
	private double getTotalCost() {
		double totalCostOfThisSolution = 0;
		for (Vehicle vehicle : this.vehicleAgendas.keySet()) {
			double totalDistanceOfThisVehicle = 0;

			City fromCity = vehicle.getCurrentCity();

			for (int i = 0; i < this.simpleVehicleAgendas.get(vehicle).size() - 1; i++) {
				City toCity = this.simpleVehicleAgendas.get(vehicle).get(i).getEndCity();
				totalDistanceOfThisVehicle += fromCity.distanceTo(toCity);
				fromCity = toCity;
			}

			double totalCostOfThisVehicle = totalDistanceOfThisVehicle * vehicle.costPerKm();
			totalCostOfThisSolution += totalCostOfThisVehicle;
		}
		return totalCostOfThisSolution;
	}
}
