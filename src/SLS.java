import java.util.ArrayList;
import java.util.List;

import logist.simulation.Vehicle;
import logist.task.TaskSet;

public class SLS { 
	private final long startTime;
	private long currentTime;
	private Solution solution;

	public SLS(List<Vehicle> vehicles, TaskSet tasks, long timeLimit) {
		this.startTime = System.currentTimeMillis();
		this.solution = selectInitialSolution(vehicles, tasks);
		this.currentTime = System.currentTimeMillis();
		
		while (this.currentTime - this.startTime < timeLimit) {
			//TODO Arthur
			
			this.currentTime = System.currentTimeMillis();
		}
	}
	
	private Solution getSolution() {
		return this.solution;
	}
	
	
	//TODO Simon
	private Solution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
		return null;
	}
	
	//TODO Simon
	private ArrayList<Solution> chooseNeighbours() {
		return null;
	}
	
	//TODO Arthur
	private Solution localChoice(ArrayList<Solution> solutions) {
		return null;
	}
}
