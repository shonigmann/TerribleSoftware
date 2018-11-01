import java.util.ArrayList;
import java.util.HashMap;

import logist.plan.Action;
import logist.simulation.Vehicle;

public class Solution {
	private HashMap<Vehicle, ArrayList<Action>> vehicleAgendas;
	
	public Solution(HashMap<Vehicle, ArrayList<Action>> vehicleAgendas) {
		this.vehicleAgendas = vehicleAgendas;
	}
	
	//TODO make methods for handy neighboring solution generation
	public HashMap<Vehicle,ArrayList<Action>> getVehicleAgendas(){
		return vehicleAgendas;
	}
}
