package simulatedHybridBlockchain;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.markers.SeriesMarkers;

import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class Main {	
	public static void main(String[] args) throws IOException {
		int limit = 5000;
		
		SimulatedNetwork network = new SimulatedNetwork();
		
		ArrayList<String> watchNodes = new ArrayList<>();
		ArrayList<Integer> watchSignals = new ArrayList<>();
		ArrayList<String> watchSpecial = new ArrayList<>();
		
		String graphedNode = null;
		ArrayList<String> nodeGraphs = new ArrayList<>();
		ArrayList<String> networkGraphs = new ArrayList<>();
		
		ArrayList<SimulatedNode> multipleNodes = new ArrayList<>();
		ArrayList<String> nodeConnections = new ArrayList<>();
		
		JsonReader reader;
		
		String filename = "default.json";
		
		try {
			filename = args[0];
		} catch(Exception e) {}
		
		try {
			InputStream in = Main.class.getClassLoader().getResourceAsStream(filename); 
			reader = new JsonReader(new InputStreamReader(in));
		} catch(Exception e) {
			System.out.println("Could not open configuration file '"+filename+"'. Recheck the filename and try again.");
			return;
		}
		
		try {
			InputStream in2 =  Main.class.getClassLoader().getResourceAsStream(args[1]); 
			BufferedReader reader2 = new BufferedReader(new InputStreamReader(in2));
			
			network.log.addOutputComparison(reader2);
			
			System.out.println("(Output comparison file detected, testing will be performed.)");
		} catch(Exception e) {
			System.out.println("Could not open comparison file! Make sure to use an absolute path, not one relative to scenario json file.");
			System.out.println("Will run this scenario without output comparisons.");
		}
		
		// Parse JSON configurations
		reader.beginObject();
		
		/**
		 * Parses inner JSON objects, and loads up configuration into the network. Possible values:
		 * name
		 * seed		(long)
		 * start 	(in ticks)
		 * end 		(in ticks)
		 * speed 	(in ticks)
		 * nodes		(describes an object with one node recipe)
		 * many_nodes 	(describes an object with multiple node recipes)
		 * logging		(describes what logging will be done to STDOUT)
		 * graphs		(describes which graphs will be shown)
		 * 
		 * Detailed instructions can be found in dissertation
		 */
		while(reader.hasNext()) {
			switch(reader.nextName()) {
				case "name":
					System.out.println("Scenario name: "+reader.nextString());
					break;
				case "seed":
					network.rng.setSeed(reader.nextLong());
					break;
				case "start":
					network.currentTick = reader.nextInt();
					break;
				case "speed":
					network.setTickSpeed(reader.nextInt());
					break;
				case "end":
					limit = reader.nextInt();
					break;
				case "many_nodes":
					reader.beginArray();
					while(reader.hasNext()) {
						reader.beginObject();
						if(reader.nextName().equals("id_prefix")) {
							String prefix = reader.nextString();
							
							if(reader.nextName().equals("class")) {
								try {
									Class<?> nodeClass = Class.forName("simulatedHybridBlockchain."+reader.nextString());
									
									int permission = 0;
									int count = 0;
									int upload = 0;
									int download = 0;
									int trans = 0;
									int minTrans = 0;
									String style = null;
									
									while(reader.hasNext()) {
										switch(reader.nextName()) {
											case "permission":
												permission = reader.nextInt();
												break;
											case "count":
												count = reader.nextInt();
												break;
											case "speedUpload":
												upload = reader.nextInt();
												break;
											case "speedDownload":
												download = reader.nextInt();
												break;
											case "transactionsPerSecond":
												trans = reader.nextInt();
												break;
											case "minTransactions":
												minTrans = reader.nextInt();
												break;
											case "style":
												style = reader.nextString();
												break;
											default:
												reader.nextString();
										}
									}
									
									for(int a = 0; a < count; a++) {
										SimulatedNode node = (SimulatedNode) nodeClass.newInstance();
										
										node.setId(prefix+"_"+a);
										
										if(upload > 0) {
											node.setUploadSpeed(upload);
										}
										if(download > 0) {
											node.setDownloadSpeed(download);
										}
										
										if(trans > 0) {
											((SimulatedSalesTransactionNode) node).setChangeForTransaction(count, trans);
										}
										
										if(minTrans > 0) {
											((SimulatedBlockWriteNode) node).setMinTransactions(minTrans);
										}
										
										if(style != null) {
											((SimulatedSalesTransactionNode) node).setStyle(style);
										}
										
										network.addNode(node);
										network.addInitialAuth(node.getId(), permission);
										multipleNodes.add(node);
									}
								} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
									System.out.println("Malformed JSON many_nodes, invalid class supplied: "+e.getMessage());
									return;
								}	
							} else {
								System.out.println("Malformed JSON many_nodes, class must be second field!");
								return;
							}							
						} else {
							System.out.println("Malformed JSON many_nodes, id_prefix must be first field!");
							return;
						}
						reader.endObject();
					}
					
					reader.endArray();
					
					break;
				case "nodes":
					// arrays of items
					reader.beginArray();
					
					while(reader.hasNext()) {
						reader.beginObject();
						
						SimulatedNode node = null;
						int connCost = 0;
						if(reader.nextName().equals("id")) {
							String id = reader.nextString();
							
							if(reader.nextName().equals("class")) {
								try {
									node = (SimulatedNode) Class.forName("simulatedHybridBlockchain."+reader.nextString()).newInstance();
									node.setId(id);
									
									while(reader.hasNext()) {
										switch(reader.nextName()) {
											case "permission":
												network.addInitialAuth(id, reader.nextInt());
												break;
											case "maxUploadConnections":
												node.setUploadConnectionLimit(reader.nextInt());
												break;
											case "maxDownloadConnections":
												node.setDownloadConnectionLimit(reader.nextInt());
												break;
											case "connectionCooldown":
												node.setConnectionCooldown(reader.nextInt());
												break;
											case "connectionStale":
												node.setConnectionStale(reader.nextInt());
												break;
											case "repetitionDisconnectThreshold":
												node.setRepetitionDisconnectThreshold(reader.nextInt());
												break;
											case "connectionCost":
												connCost = reader.nextInt();
												break;
											case "ticksPerTransaction":
												if(node instanceof SimulatedTransactionNode) {
													((SimulatedTransactionNode) node).setTicksPerTransaction(reader.nextInt());
												}
												break;
											default:
												reader.nextString();
										}
									}
								} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
									System.out.println("Malformed JSON node, invalid class supplied: "+e.getMessage());
									return;
								}	
							} else {
								System.out.println("Malformed JSON node, class must be second field!");
								return;
							}
						} else {
							System.out.println("Malformed JSON node, id must be first field!");
							return;
						}
						
						network.addNode(node);
						multipleNodes.add(node);
						
						if(connCost != 0) {
							network.nodeConnectionCosts.put(node.getId(), connCost);
						}
						
						reader.endObject();
					}
					
					reader.endArray();
					break;
					
				case "logging":
					reader.beginObject();
					while(reader.hasNext()) {
						switch(reader.nextName()) {
							case "signals":
								reader.beginArray();
	
								while(reader.hasNext()) {
									String sig = reader.nextString();
									
									try {
										watchSignals.add(NodeSignal.class.getDeclaredField(sig+"_SIGNAL").getInt(null));
									} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
										System.out.println("Invalid signal '"+sig+"' declared. Make sure to use signals without _SIGNAL appended.");
										return;
									}
								}
								
								reader.endArray();
								break;
								
							case "nodes":
								reader.beginArray();
								
								while(reader.hasNext()) {
									watchNodes.add(reader.nextString());
								}
								
								reader.endArray();
								
								break;
							case "special":
								reader.beginArray();
								while(reader.hasNext()) {
									watchSpecial.add(reader.nextString());
								}
								reader.endArray();
								
								break;
						}
					}
					reader.endObject();
					break;
				case "graphs":
					reader.beginObject();
					
					switch(reader.nextName()) {
						case "network":
							reader.beginArray();
							while(reader.hasNext()) {
								networkGraphs.add(reader.nextString());
							}
							reader.endArray();
							break;
						case "node":
							reader.beginArray();
							
							graphedNode = reader.nextString();
							
							while(reader.hasNext()) {
								nodeGraphs.add(reader.nextString());
							}
							reader.endArray();
							break;
					}
					
					reader.endObject();
					
					break;
				case "connections":
					reader.beginArray();
					while(reader.hasNext()) {
						nodeConnections.add(reader.nextString());
					}
					reader.endArray();
					break;
				default:
					reader.skipValue();
					break;
			}
		}
		
		reader.close();
		
		network.log.addNodeWatch(watchNodes);
		network.log.addSignalWatch(watchSignals);
		network.log.addSpecialWatch(watchSpecial);
		
		System.out.println("Scenario loaded successfully!");
		
		for(SimulatedNode node: multipleNodes) {
			node.addNetwork(network);
		}
		
		if(nodeConnections.size() > 0) {
			for(String conn: nodeConnections) {
				String[] data = conn.split(" ");
				SimulatedNode from = network.nodes.getOrDefault(data[0], null);
				SimulatedNode to = network.nodes.getOrDefault(data[1], null);
				
				int delay = network.getConnectionDelay(from.getId(), to.getId());
				
				if(data.length == 4) {
					delay = Integer.parseInt(data[3]);
				}
				
				switch(data[2]) {
					case "U":
						to.establishDownloadConnection(from.getId(), delay);
						from.activeOutgoingConnections.put(to.getId(), delay);
						break;
					case "D":
						to.establishUploadConnection(from.getId(), delay);
						from.activeIncomingConnections.put(to.getId(), delay);
						break;
					case "UD":
					case "DU":
						to.establishDownloadConnection(from.getId(), delay);
						to.establishUploadConnection(from.getId(), delay);
						
						from.activeOutgoingConnections.put(to.getId(), delay);
						from.activeIncomingConnections.put(to.getId(), delay);
						break;
				}
			}
		}
		
		// setup network graphs
		ArrayList<CategoryChart> charts = new ArrayList<CategoryChart>();
		
		SwingWrapper<CategoryChart> network_charts = null;
		CategoryChart chart1 = null;
		CategoryChart chart2 = null;
		CategoryChart chart3 = null;
		CategoryChart chart4 = null;
		CategoryChart chart5 = null;
		CategoryChart chart6 = null;
		CategoryChart chart7 = null;
		CategoryChart chart8 = null;
		CategoryChart chart9 = null;
		
		ArrayList<XYChart> charts2 = new ArrayList<XYChart>();
		
		SwingWrapper<XYChart> node_charts = null;
		XYChart node_chart1 = null;
		XYChart node_chart2 = null;
		XYChart node_chart3 = null;
		XYChart node_chart4 = null;
		XYChart node_chart5 = null;
		XYChart node_chart6 = null;
		XYChart node_chart7 = null;
		XYChart node_chart8 = null;
		XYChart node_chart9 = null;
		
		// populate network graphs
		for(String graph_id: networkGraphs) {
			switch(graph_id) {
				case "transaction_pool":
					double[][] initdata = network.getWaitingTransactions();
					
					chart1 = new CategoryChartBuilder().width(500).height(300).title("Node waiting transactions").build();
				    chart1.addSeries("transaction pool", initdata[0], initdata[1]);
				    charts.add(chart1);
				    
					break;
					
				case "forward_signals":
					double[][] initdata2 = network.getWaitingForwardTransactions();
					
					chart2 = new CategoryChartBuilder().width(500).height(300).title("Node forward signals").build();
				    chart2.addSeries("forward signals", initdata2[0], initdata2[1]);
				    charts.add(chart2);
				    
					break;
					
				case "connections":
					double[][] initdata3 = network.getActiveUploads();
					double[][] initdata4 = network.getActiveDownloads();
					
					chart3 = new CategoryChartBuilder().width(500).height(300).title("Node connections").build();
				    chart3.addSeries("uploads", initdata3[0], initdata3[1]);
				    chart3.addSeries("downloads", initdata4[0], initdata4[1]);
				    charts.add(chart3);
				    
					break;
					
				case "signals_received":
					double[][] initdata5 = network.getSignalsReceived(1);
					double[][] initdata6 = network.getSignalsReceived(0);
					double[][] initdata7 = network.getSignalsReceived(3);
					
					chart4 = new CategoryChartBuilder().width(500).height(300).title("Signals received").build();
				    chart4.addSeries("BLOCK", initdata5[0], initdata5[1]);
				    chart4.addSeries("ACTIVE", initdata6[0], initdata6[1]);
				    chart4.addSeries("TRANSACTION", initdata7[0], initdata7[1]);
				    charts.add(chart4);
					
					break;
					
				case "signals_sent":
					double[][] initdata8 = network.getSignalsSent(1);
					double[][] initdata9 = network.getSignalsSent(0);
					double[][] initdata10 = network.getSignalsSent(3);
					
					chart5 = new CategoryChartBuilder().width(500).height(300).title("Signals sent").build();
				    chart5.addSeries("BLOCK", initdata8[0], initdata8[1]);
				    chart5.addSeries("ACTIVE", initdata9[0], initdata9[1]);
				    chart5.addSeries("TRANSACTION", initdata10[0], initdata10[1]);
				    charts.add(chart5);
				    
					break;
					
				case "waiting_signals":
					double[][] initdata11 = network.getWaitingSignals();
					
					chart6 = new CategoryChartBuilder().width(500).height(300).title("Node signals waiting for processing").build();
				    chart6.addSeries("waiting signals", initdata11[0], initdata11[1]);
				    charts.add(chart6);
					
					break;
					
				case "connection_speeds":
					double[][] initdata13 = network.getUploadUtilization();
					double[][] initdata14 = network.getDownloadUtilization();
					
					chart8 = new CategoryChartBuilder().width(500).height(300).title("Node connection utilization [bits/tick]").build();
				    chart8.addSeries("upload", initdata13[0], initdata13[1]);
				    chart8.addSeries("download", initdata14[0], initdata14[1]);
				    charts.add(chart8);
					
					break;
					
				case "blocks_accepted":
					double[][] initdata12 = network.getBlockHeight();
					
					chart7 = new CategoryChartBuilder().width(500).height(300).title("Node blockchain height").build();
				    chart7.addSeries("blocks", initdata12[0], initdata12[1]);
				    charts.add(chart7);
				    
					break;
					
				case "transactions_verified":
					double[][] initdata15 = network.getVerifiedTransactions();
					
					chart9 = new CategoryChartBuilder().width(500).height(300).title("Node total verified transactions").build();
				    chart9.addSeries("verified", initdata15[0], initdata15[1]);
				    charts.add(chart9);
					    
					break;
			}
		}

		if(charts.size() > 0) {
			network_charts = new SwingWrapper<CategoryChart>(charts);
			network_charts.displayChartMatrix("Network graphs");
		}
		
		ArrayList<Double> network_ticks = new ArrayList<>();
		
		ArrayList<Double> transaction_pool_data = new ArrayList<>();
		ArrayList<Double> forward_signals_data = new ArrayList<>();
		ArrayList<Double> connections_up_data = new ArrayList<>();
		ArrayList<Double> connections_down_data = new ArrayList<>();
		
		ArrayList<Double> signals_rec_block_data = new ArrayList<>();
		ArrayList<Double> signals_rec_active_data = new ArrayList<>();
		ArrayList<Double> signals_rec_transaction_data = new ArrayList<>();
		
		ArrayList<Double> signals_sent_block_data = new ArrayList<>();
		ArrayList<Double> signals_sent_active_data = new ArrayList<>();
		ArrayList<Double> signals_sent_transaction_data = new ArrayList<>();
		
		ArrayList<Double> signals_waiting_data = new ArrayList<>();
		ArrayList<Double> blocks_accepted_data = new ArrayList<>();
		ArrayList<Double> transactions_verified_data = new ArrayList<>();
		
		ArrayList<Double> speed_upload_data = new ArrayList<>();
		ArrayList<Double> speed_download_data = new ArrayList<>();
		
		
		network_ticks.add((double)network.currentTick);
		// populate node graphs
		for(String graph_id: nodeGraphs) {
			switch(graph_id) {
				case "transaction_pool":
					transaction_pool_data.add(network.getWaitingTransactions(graphedNode));
					
					node_chart1 = new XYChartBuilder().width(500).height(300).title("Node waiting transactions").build();
					node_chart1.addSeries("transaction pool", network_ticks, transaction_pool_data).setMarker(SeriesMarkers.NONE);
					
					charts2.add(node_chart1);
				    
					break;
					
				case "forward_signals":
					forward_signals_data.add(network.getWaitingForwardTransactions(graphedNode));
					
					node_chart2 = new XYChartBuilder().width(500).height(300).title("Node forward signals").build();
					node_chart2.addSeries("forward signals", network_ticks, forward_signals_data).setMarker(SeriesMarkers.NONE);
					charts2.add(node_chart2);
				    
					break;
					
				case "connections":
					connections_up_data.add(network.getActiveUploads(graphedNode));
					connections_down_data.add(network.getActiveDownloads(graphedNode));
					
					node_chart3 = new XYChartBuilder().width(500).height(300).title("Node connections").build();
					node_chart3.addSeries("uploads", network_ticks, connections_up_data).setMarker(SeriesMarkers.NONE);
				    node_chart3.addSeries("downloads", network_ticks, connections_down_data).setMarker(SeriesMarkers.NONE);
				    charts2.add(node_chart3);
				    
					break;
					
				case "signals_received":
					signals_rec_block_data.add(network.getSignalsReceived(graphedNode, 1));
					signals_rec_active_data.add(network.getSignalsReceived(graphedNode, 0));
					signals_rec_transaction_data.add(network.getSignalsReceived(graphedNode, 3));
					
					node_chart4 = new XYChartBuilder().width(500).height(300).title("Signals received").build();
					node_chart4.addSeries("BLOCK", network_ticks, signals_rec_block_data).setMarker(SeriesMarkers.NONE);
					node_chart4.addSeries("ACTIVE", network_ticks, signals_rec_active_data).setMarker(SeriesMarkers.NONE);
					node_chart4.addSeries("TRANSACTION", network_ticks, signals_rec_transaction_data).setMarker(SeriesMarkers.NONE);
					charts2.add(node_chart4);
					
					break;
					
				case "signals_sent":
					signals_sent_block_data.add(network.getSignalsSent(graphedNode, 1));
					signals_sent_active_data.add(network.getSignalsSent(graphedNode, 0));
					signals_sent_transaction_data.add(network.getSignalsSent(graphedNode, 3));
					
					node_chart5 = new XYChartBuilder().width(500).height(300).title("Signals sent").build();
					node_chart5.addSeries("BLOCK", network_ticks, signals_sent_block_data).setMarker(SeriesMarkers.NONE);
					node_chart5.addSeries("ACTIVE", network_ticks, signals_sent_active_data).setMarker(SeriesMarkers.NONE);
					node_chart5.addSeries("TRANSACTION", network_ticks, signals_sent_transaction_data).setMarker(SeriesMarkers.NONE);
					charts2.add(node_chart5);
				    
					break;
					
				case "waiting_signals":
					signals_waiting_data.add(network.getWaitingSignals(graphedNode));
					
					node_chart6 = new XYChartBuilder().width(500).height(300).title("Node signals waiting for processing").build();
					node_chart6.addSeries("waiting signals", network_ticks, signals_waiting_data).setMarker(SeriesMarkers.NONE);
					charts2.add(node_chart6);
					
					break;
					
				case "connection_speeds":
					speed_upload_data.add(network.getUploadUtilization(graphedNode));
					speed_download_data.add(network.getDownloadUtilization(graphedNode));
					
					node_chart8 = new XYChartBuilder().width(500).height(300).title("Node connection utilization [bits/tick]").build();
					node_chart8.addSeries("upload", network_ticks, speed_upload_data).setMarker(SeriesMarkers.NONE);
					node_chart8.addSeries("download", network_ticks, speed_download_data).setMarker(SeriesMarkers.NONE);
					charts2.add(node_chart8);
					
					break;
					
				case "blocks_accepted":
					blocks_accepted_data.add(network.getBlockHeight(graphedNode));
					
					node_chart7 = new XYChartBuilder().width(500).height(300).title("Node blockchain height").build();
					node_chart7.addSeries("blocks", network_ticks, blocks_accepted_data).setMarker(SeriesMarkers.NONE);
					charts2.add(node_chart7);
				    
					break;
					
				case "transactions_verified":
					transactions_verified_data.add(network.getVerifiedTransactions(graphedNode));
					
					node_chart9 = new XYChartBuilder().width(500).height(300).title("Node total verified transactions").build();
					node_chart9.addSeries("verified", network_ticks, transactions_verified_data).setMarker(SeriesMarkers.NONE);
				    charts2.add(node_chart9);
					    
					break;
			}
		}

		if(charts2.size() > 0) {
			node_charts = new SwingWrapper<XYChart>(charts2);
			node_charts.displayChartMatrix("Node '"+graphedNode+"' charts");
		}
	    
		// delay for two seconds to allow for graphs to show up
	    try {
	    	if(charts.size() > 0 || charts2.size() > 0) {
	    		Thread.sleep(2000);
	    	}
		} catch (InterruptedException e) {}
	    
	    System.out.println("Simulating network for "+limit+" ticks ...");
	    
	    while(true) {
	    	network.simulate();
	    	if(network.getCurrentTick() % 1000 == 0) {
	    		System.out.println("NETWORK: "+network.getCurrentTick());
	    	}
	    	
	    	
	    	for(String graph_id: networkGraphs) {
				switch(graph_id) {
					case "transaction_pool":
						final double[][] data = network.getWaitingTransactions();
						chart1.updateCategorySeries("transaction pool", data[0], data[1], null);
					    
						break;
						
					case "forward_signals":
						final double[][] data2 = network.getWaitingForwardTransactions();
						chart2.updateCategorySeries("forward signals", data2[0], data2[1], null);
					    
						break;
						
					case "connections":
						final double[][] data3 = network.getActiveUploads();
						final double[][] data4 = network.getActiveDownloads();
						chart3.updateCategorySeries("uploads", data3[0], data3[1], null);
						chart3.updateCategorySeries("downloads", data4[0], data4[1], null);
					    
						break;
						
					case "signals_received":
						final double[][] data5 = network.getSignalsReceived(1);
						final double[][] data6 = network.getSignalsReceived(0);
						final double[][] data7 = network.getSignalsReceived(3);
						chart4.updateCategorySeries("BLOCK", data5[0], data5[1], null);
						chart4.updateCategorySeries("ACTIVE", data6[0], data6[1], null);
						chart4.updateCategorySeries("TRANSACTION", data7[0], data7[1], null);
						
						break;
						
					case "signals_sent":
						final double[][] data8 = network.getSignalsSent(1);
						final double[][] data9 = network.getSignalsSent(0);
						final double[][] data10 = network.getSignalsSent(3);
						chart5.updateCategorySeries("BLOCK", data8[0], data8[1], null);
						chart5.updateCategorySeries("ACTIVE", data9[0], data9[1], null);
						chart5.updateCategorySeries("TRANSACTION", data10[0], data10[1], null);
					    
						break;
						
					case "waiting_signals":
						final double[][] data11 = network.getWaitingSignals();
						chart6.updateCategorySeries("waiting signals", data11[0], data11[1], null);
						
						break;
						
					case "connection_speeds":
						final double[][] data13 = network.getUploadUtilization();
						final double[][] data14 = network.getDownloadUtilization();
						chart8.updateCategorySeries("upload", data13[0], data13[1], null);
						chart8.updateCategorySeries("download", data14[0], data14[1], null);
						
						break;
						
					case "blocks_accepted":
						final double[][] data12 = network.getBlockHeight();
						chart7.updateCategorySeries("blocks", data12[0], data12[1], null);
					    
						break;
						
					case "transactions_verified":
						final double[][] data15 = network.getVerifiedTransactions();
						chart9.updateCategorySeries("verified", data15[0], data15[1], null);
						    
						break;
				}
			}
	    	
	    	network_ticks.add((double)network.currentTick);
	    	
	    	for(String graph_id: nodeGraphs) {
				switch(graph_id) {
					case "transaction_pool":
						transaction_pool_data.add(network.getWaitingTransactions(graphedNode));
						node_chart1.updateXYSeries("transaction pool", network_ticks, transaction_pool_data, null);
						
						break;
						
					case "forward_signals":
						forward_signals_data.add(network.getWaitingForwardTransactions(graphedNode));
						node_chart2.updateXYSeries("forward signals", network_ticks, forward_signals_data, null);
					    
						break;
						
					case "connections":
						connections_up_data.add(network.getActiveUploads(graphedNode));
						connections_down_data.add(network.getActiveDownloads(graphedNode));
						node_chart3.updateXYSeries("uploads", network_ticks, connections_up_data, null);
						node_chart3.updateXYSeries("downloads", network_ticks, connections_down_data, null);
					    
						break;
						
					case "signals_received":
						signals_rec_block_data.add(network.getSignalsReceived(graphedNode, 1));
						signals_rec_active_data.add(network.getSignalsReceived(graphedNode, 0));
						signals_rec_transaction_data.add(network.getSignalsReceived(graphedNode, 3));
						node_chart4.updateXYSeries("BLOCK", network_ticks, signals_rec_block_data, null);
						node_chart4.updateXYSeries("ACTIVE", network_ticks, signals_rec_active_data, null);
						node_chart4.updateXYSeries("TRANSACTION", network_ticks, signals_rec_transaction_data, null);
						
						break;
						
					case "signals_sent":
						signals_sent_block_data.add(network.getSignalsSent(graphedNode, 1));
						signals_sent_active_data.add(network.getSignalsSent(graphedNode, 0));
						signals_sent_transaction_data.add(network.getSignalsSent(graphedNode, 3));
						node_chart5.updateXYSeries("BLOCK", network_ticks, signals_sent_block_data, null);
						node_chart5.updateXYSeries("ACTIVE", network_ticks, signals_sent_active_data, null);
						node_chart5.updateXYSeries("TRANSACTION", network_ticks, signals_sent_transaction_data, null);
					    
						break;
						
					case "waiting_signals":
						signals_waiting_data.add(network.getWaitingSignals(graphedNode));
						node_chart6.updateXYSeries("waiting signals", network_ticks, signals_waiting_data, null);
						
						break;
						
					case "connection_speeds":
						speed_upload_data.add(network.getUploadUtilization(graphedNode));
						speed_download_data.add(network.getDownloadUtilization(graphedNode));
						node_chart8.updateXYSeries("upload", network_ticks, speed_upload_data, null);
						node_chart8.updateXYSeries("download", network_ticks, speed_download_data, null);
						
						break;
						
					case "blocks_accepted":
						blocks_accepted_data.add(network.getBlockHeight(graphedNode));
						node_chart7.updateXYSeries("blocks", network_ticks, blocks_accepted_data, null);
					    
						break;
						
					case "transactions_verified":
						transactions_verified_data.add(network.getVerifiedTransactions(graphedNode));
						node_chart9.updateXYSeries("verified", network_ticks, transactions_verified_data, null);
						    
						break;
				}
			}
	    	
	    	if(network_charts != null) {
    			for(int a = 0; a < charts.size(); a++) {
    				network_charts.repaintChart(a);
    			}
	    	}
	    	
	    	if(node_charts != null) {
    			for(int a = 0; a < charts2.size(); a++) {
    				node_charts.repaintChart(a);
    			}
	    	}
			
			if(network.getCurrentTick() >= limit) {
				break;
			}
	    }
	    
	    System.out.println("Simulation finished.");
	    
	    network.log.getComparisonStats();
	    
	    // == output statistics
	    // 		total_verified transactions
	    //		blocks_accepted
	    // 		average_block transaction count
	    //		average_block size 
	    
	    ArrayList<Integer> verifiedTransactions = network.getFinalVerifiedTransactions();
	    ArrayList<Integer> blocksAccepted = network.getFinalBlocksAccepted();
	    ArrayList<Integer> blockTransactionCounts = network.getFinalBlockTransactionCounts();
	    ArrayList<Integer> blockSizes = network.getFinalBlockSizes();
	    
	    System.out.println("Statistics");
	    System.out.printf("%1$30s %2$10s %3$10s %4$10s %5$10s %6$10s\n", "variable", "mean", "mode", "median", "min", "max");
	    System.out.printf("%1$30s %2$10d %3$10d %4$10d %5$10d %6$10d\n", "Verified Transactions", Main.mean(verifiedTransactions), Main.mode(verifiedTransactions), Main.median(verifiedTransactions), Main.min(verifiedTransactions), Main.max(verifiedTransactions));
	    System.out.printf("%1$30s %2$10d %3$10d %4$10d %5$10d %6$10d\n", "Blocks accepted", Main.mean(blocksAccepted), Main.mode(blocksAccepted), Main.median(blocksAccepted), Main.min(blocksAccepted), Main.max(blocksAccepted));
	    System.out.printf("%1$30s %2$10d %3$10d %4$10d %5$10d %6$10d\n", "Transactions in blocks", Main.mean(blockTransactionCounts), Main.mode(blockTransactionCounts), Main.median(blockTransactionCounts), Main.min(blockTransactionCounts), Main.max(blockTransactionCounts));
	    System.out.printf("%1$30s %2$10d %3$10d %4$10d %5$10d %6$10d\n", "Block sizes (bits)", Main.mean(blockSizes), Main.mode(blockSizes), Main.median(blockSizes), Main.min(blockSizes), Main.max(blockSizes));
	}
	
	public static int mean(ArrayList<Integer> values) {
		int count = values.size();
		int val = 0;
		
		for(int x: values) {
			val += x;
		}
		
		return val/(count > 0 ? count : 1);
	}
	
	public static int mode(ArrayList<Integer> values) {
		HashMap<Integer, Integer> frequencies = new HashMap<>();
		
		for(int val: values) {
			frequencies.put(val, frequencies.getOrDefault(val,0) + 1);
		}
		
		int mostFrequent = -1;
		
		for(int candidate: frequencies.keySet()) {
			if(frequencies.get(candidate) > frequencies.getOrDefault(mostFrequent, 0)) {
				mostFrequent = candidate;
			}
		}
		
		return mostFrequent;
	}
	
	public static int median(ArrayList<Integer> values) {
		Collections.sort(values);
		return values.size() > 0 ? values.get(values.size()/2) : 0;
	}
	
	public static int min(ArrayList<Integer> values) {
		int leastFrequent = -1;
		
		for(int count: values) {
			if(count < leastFrequent || leastFrequent == -1) {
				leastFrequent = count;
			}
		}
		
		return leastFrequent;
	}
	
	public static int max(ArrayList<Integer> values) {
		int mostFrequent = -1;
		
		for(int count: values) {
			if(count > mostFrequent) {
				mostFrequent = count;
			}
		}
		
		return mostFrequent;
	}
}
