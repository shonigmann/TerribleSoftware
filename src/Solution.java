import java.util.ArrayList;
import java.util.HashMap;

import logist.plan.Action;
import logist.simulation.Vehicle;

public class Solution {
	private HashMap<Vehicle, ArrayList<Action>> vehicleAgendas;
	
	public Solution(HashMap<Vehicle, ArrayList<Action>> vehicle2Agendas) {
		this.vehicleAgendas = vehicle2Agendas;
	}
	
	//TODO make methods for handy neighbouring solution generation
}
