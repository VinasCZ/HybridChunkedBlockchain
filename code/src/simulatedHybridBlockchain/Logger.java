package simulatedHybridBlockchain;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

public class Logger {

	private HashSet<String> watchNodes = new HashSet<>();
	private HashSet<Integer> watchSignals = new HashSet<>();
	private HashSet<String> watchSpecial = new HashSet<>();
	
	private BufferedReader reader = null;
	private int lineCounter = 0;
	private int correctLines = 0;
	
	public void addNodeWatch(ArrayList<String> watchNodes) {
		this.watchNodes.addAll(watchNodes);
	}
	
	public void addSignalWatch(ArrayList<Integer> watchSignals) {
		this.watchSignals.addAll(watchSignals);
	}
	
	public void addSpecialWatch(ArrayList<String> watchSpecial2) {
		this.watchSpecial.addAll(watchSpecial2);
	}

	public void regular(String origin, String message, int delay) {
		if(this.watchNodes.contains(origin)) {
			this.print(origin, message, delay);
		}
	}
	
	public void special(String origin, String type, String message, int delay) {
		if(this.watchSpecial.contains(type)) {
			this.print(origin, message, delay);
		}
	}
	
	public void signal(String origin, String target, NodeSignal signal, int delay) {
		if(this.watchSignals.contains(signal.getType()) || this.watchNodes.contains(origin)) {
			this.print(origin, "-> "+target+" (sig "+signal.getType()+") hash: "+signal.getHash()+", payload: "+signal.getPayload(), delay);
		}
	}
	
	private void print(String origin, String message, int delay) {
		String msg = delay+": "+origin+" "+message;
		if(this.reader != null) {
			try {
				String line = this.reader.readLine();
				if(!line.equals(msg)) {
					System.out.println(lineCounter+" EXPECTED: "+line);
					System.out.println(lineCounter+" RECEIVED: "+msg);
				} else {
					correctLines++;
					// System.out.println(lineCounter+" OK: "+msg);
				}
			} catch (IOException|NullPointerException e) {
				System.out.println(lineCounter+" EXPECTED: <End of comparison file!>");
				System.out.println(lineCounter+" RECEIVED: "+msg);
			}
		} else {
			System.out.println(msg);
		}
		
		lineCounter++;
	}

	public void addOutputComparison(BufferedReader reader2) {
		this.reader = reader2;
	}

	public void getComparisonStats() {
		if(reader != null) {
			if(lineCounter == correctLines) {
				if(lineCounter == 0) {
					System.out.println("Test did not capture any lines for comparison.");
				} else {
					System.out.println("Test SUCCESS, "+correctLines+"/"+lineCounter+" valid");
				}
			} else {
				System.out.println("Test FAIL, "+(lineCounter-correctLines)+"/"+lineCounter+" invalid");
			}
		}
	}
}
