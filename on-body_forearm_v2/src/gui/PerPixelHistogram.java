package gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import processing.core.PApplet;
import processing.core.PVector;
import SimpleOpenNI.SimpleOpenNI;

public class PerPixelHistogram {
	
	
//	private SimpleOpenNI context;
	
	private HashMap<Integer, PVector> ppHist = new HashMap<Integer,PVector>();
	private HashMap<Integer, PVector> smoothedPts = new HashMap<Integer,PVector>();
	private ArrayList<HashMap<Integer,PVector>> ptLists = new ArrayList<HashMap<Integer,PVector>>();
	private HashSet<Integer> indices = new HashSet<Integer>();
	
	private int[] depth;
	
	private int minDepth = Integer.MAX_VALUE;
	private int maxDepth = Integer.MIN_VALUE;
	private int minValue = Integer.MAX_VALUE;
	private int maxValue = Integer.MIN_VALUE;
	
	private int nearThreshold;
	private int farThreshold;
	private int smoothing = 1;
	private int counter = 0;
	
	private Lock lock = new ReentrantLock();
		
	private boolean isInitialized = false;
	
	
	/**
	 * Creates a new per pixel histogram within a given depth range.
	 * 
	 * @param nearThreshold
	 * @param farThreshold
	 * @param span
	 * @param bins
	 */
	public PerPixelHistogram(int nearThreshold, int farThreshold){
		this.nearThreshold 	 = nearThreshold;
		this.farThreshold	 = farThreshold;	
	}
	
	/**
	 * Calculate the depth histogram based on new depth map. </br>
	 * Changes to depth array will modify the kinect's depth map.
	 * @param context - SimpleOpenNI object
	 */
	public void update(SimpleOpenNI context){
		depth = context.depthMap();
		
		filterOutThreshold(nearThreshold, farThreshold);	
		
		calcHistogram(context, nearThreshold, farThreshold);
		
		// average out the point values
		// remember the last n-sets of points
		if (ptLists.size() < smoothing)
			ptLists.add(copy(ppHist));
		else
			ptLists.set(counter%smoothing, copy(ppHist));
		counter++;


		if (counter > smoothing){
			smoothPts();
			findMinMaxValue(smoothedPts);
		}
	}
	
	/**
	 * To display 3D points in DepthCAM
	 * @param p5
	 */
	public void display(PApplet p5){
		p5.noStroke();
		p5.fill(255);
		
		if (counter > smoothing){
			lock.lock();
			try{				
				for(PVector p : smoothedPts.values()){
					
					p5.pushMatrix();
					p5.translate(p.x,p.y,p.z);
					p5.box(5);
					p5.popMatrix();
					
				}						
			}catch (Exception e){
				System.err.println(e);
			}finally{lock.unlock();}	
		}
	}
	
	/**
	 * Maps depth data outside the given threshold to 0.
	 * 
	 * @param near - nearThreshold
	 * @param far - farThreshold
	 */
	private void filterOutThreshold(int near, int far){
		
		for (int i=0; i<depth.length; i++){
			if (depth[i] < near || depth[i] > far)	
				depth[i] = 0;		
		}
		
	}
	
	
	/**
	 * Stores the depth map index and real world point of all
	 * points within the threshold.
	 * @param context 
	 * 
	 * @param near	 - near threshold
	 * @param far	 - far threshold
	 */
	private void calcHistogram(SimpleOpenNI context, int near, int far){
		
		ppHist.clear();	
		
		// rebuild the hashmap
		lock.lock();
		try{
	
			// record each index and real world point
			for(int i=0; i<depth.length; i++){

				if (depth[i] != 0){		

					// put the index and point into the map
					PVector p = context.depthMapRealWorld()[i];
					ppHist.put(i, p);
					
					// keep track of all our indices into the depth map
					if (!indices.contains(i))
						indices.add(i);
				}
			}
		}catch (Exception e){					
			System.err.println(e);
		}finally{lock.unlock();}

		findMinMaxValue(ppHist);
	}
	
	/**
	 * Smooth the incoming 3D points.
	 */
	private void smoothPts(){
		lock.lock();
		smoothedPts.clear();
//		indicies.clear(); // not right.
		
		try{			
//			// put all the values into  smoothedPts
//			for (int i=0; i<smoothing; i++){
//				HashMap<Integer,PVector> pts = ptLists.get(i);
//				for (int index : pts.keySet()){
//					
//					if (!smoothedPts.containsKey(index)){
//						smoothedPts.put(index, pts.get(index));
//					}
//					else{
//						PVector p = new PVector(pts.get(index).x, pts.get(index).y, pts.get(index).z);
//						PVector p0 = new PVector(smoothedPts.get(index).x, smoothedPts.get(index).y, smoothedPts.get(index).z);
//						p.add(p0);
//						smoothedPts.put(index, p);
//					}
//				}
//			}
			
			
			
			// for each one of our recorded indices
			for (int index : indices){
				// find all points recorded there and average
				PVector p = new PVector();
				for (int i=0; i<smoothing; i++){
					if (ptLists.get(i).containsKey(index))
						p.add(ptLists.get(i).get(index));
				}
				p.div(smoothing);
				
				
				
				// then store in smoothed points
				smoothedPts.put(index, p);
			}
			
			
			for (int index : indices){
				PVector p = smoothedPts.get(index);
				// delete old points
				if (p.x == 0 && p.y == 0 && p.z == 0){
					smoothedPts.remove(index);
					indices.remove(index);
				}
			}
			
			
			System.out.println("indices.size(): "+indices.size());
			System.out.println("smoothedPts.size(): "+smoothedPts.size());
			
			
			// average values
			for (PVector p : smoothedPts.values()){
				p.div(smoothing);
				
				if (p.x == 0 && p.y == 0 && p.z == 0){
					
					// remove value from map and indices set
					
				}
					
			}
			
		}catch (Exception e){
			System.err.println(e);
		}finally{lock.unlock();}	
		
	}
	
	/**
	 * Finds the min/max Z-values of the stored points.
	 * 
	 * Should be the near/far thresholds.
	 */
	private void findMinMaxValue(HashMap<Integer,PVector> map){
		
		minValue = Integer.MAX_VALUE;
		maxValue = Integer.MIN_VALUE;
		
		lock.lock();		
		try{
			for (PVector c: map.values()){
				
				if (c.z < minValue)
					minValue = (int) c.z;
				if (c.z > maxValue)
					maxValue = (int) c.z;
			}
		}catch (Exception e){
			System.err.println(e);
		}finally{lock.unlock();}
		
		
//		System.out.println("minValue: "+minValue);
//		System.out.println("maxValue: "+maxValue);
		
	}
	
	
	private HashMap<Integer, PVector> copy(HashMap<Integer, PVector> map){
		
		HashMap<Integer,PVector> temp = new HashMap<Integer,PVector>();
		
		lock.lock();		
		try{
			for (int key : map.keySet())
				temp.put(key, map.get(key));
		}catch (Exception e){
			System.err.println(e);
		}finally{lock.unlock();}
		
		return temp;
		
	}
	
}
