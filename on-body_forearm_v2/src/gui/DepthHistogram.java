package gui;

import java.awt.Frame;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import processing.core.PVector;
import toxi.geom.Vec3D;
import SimpleOpenNI.SimpleOpenNI;

public class DepthHistogram {

	private SimpleOpenNI context;
	
	private HashMap<Integer,Integer> dHistogram = new HashMap<Integer,Integer>();
	
	private int[] depth;
	private int bins;
	private int span;
	
	private int minDepth = Integer.MAX_VALUE;
	private int maxDepth = Integer.MIN_VALUE;
	private int minValue = Integer.MAX_VALUE;
	private int maxValue = Integer.MIN_VALUE;
	
	private int nearThreshold;
	private int farThreshold;
	
	private Lock lock = new ReentrantLock();
		
	private boolean isInitialized = false;
	
	
	/**
	 * Creates a new depth histogram within a given depth range.
	 * 
	 * @param nearThreshold
	 * @param farThreshold
	 * @param span
	 * @param bins
	 */
	public DepthHistogram(int nearThreshold, int farThreshold, int span, int bins){
		this.nearThreshold 	 = nearThreshold;
		this.farThreshold	 = farThreshold;	
		this.span			 = span;
		this.bins		 	 = bins;
	}
	
	
	/**
	 * Calculate the depth histogram based on new depth map. </br>
	 * Changes to depth array will modify the kinect's depth map.
	 * @param context - SimpleOpenNI object
	 */
	public void update(SimpleOpenNI context){
		depth = context.depthMap();
		
		if (!isInitialized){
			this.context = context;
			isInitialized = true;
		}
		
		filterOutThreshold(nearThreshold, farThreshold);	
		dHistogram.clear();	
		calcHistogram(nearThreshold, farThreshold);

	}
	
	/**
	 * Generates the depth histogram within a given threshold.
	 * 
	 * @param near	 - near threshold
	 * @param far	 - far threshold
	 */
	private void calcHistogram(int near, int far){

		// build bins
		lock.lock();
		try{
			for (int i=0; i<bins+1; i++){
				dHistogram.put((int) (i*span),0);	
			}

			// for each depth value, find the closest bins and add to count
			for(int i=0; i<depth.length; i++){

				if (depth[i] != 0){

					int bin = (int) ((depth[i] - near)/span);				

					// add to the count
					if (dHistogram.containsKey(bin*span))
						dHistogram.put((int) (bin*span), dHistogram.get(bin*span)+1);

				}
			}
		}catch (Exception e){					
			System.err.println(e);
		}finally{lock.unlock();}

		findMinMaxCount(dHistogram);
	}

	
	/**
	 * Maps depth data outside the given threshold to 0.
	 * 
	 * @param near - nearThreshold
	 * @param far - farThreshold
	 */
	private void filterOutThreshold(int near, int far){
		
		for (int i=0; i<depth.length; i++){

			if (depth[i] < near || depth[i] > far){		

				depth[i] = 0;
			}
		}
		
	}
	
	
	/**
	 * Gets the min/max depth in the current depth map.
	 */
	private void findMinMaxDepth(){

		for (int i=0; i<depth.length; i++){

			if (depth[i] < minDepth && depth[i] != 0)
				minDepth = depth[i];		
			if (depth[i] > maxDepth)
				maxDepth = depth[i];
		}

//		System.out.println("minDepth: "+minDepth);
//		System.out.println("max: "+maxDepth);
	}

	/**
	 * Gets the min/max of the stored values
	 */
	private void findMinMaxCount(HashMap<Integer,Integer> hist){
		
		minValue = Integer.MAX_VALUE;
		maxValue = Integer.MIN_VALUE;
		
		lock.lock();		
		try{
			for (int c: hist.values()){
				
				if (c < minValue)
					minValue = (int) c;
				if (c > maxValue)
					maxValue = (int) c;
			}
		}catch (Exception e){
			System.err.println(e);
		}finally{lock.unlock();}
		
		
//		System.out.println("minValue: "+minValue);
//		System.out.println("maxValue: "+maxValue);
		
	}
	

	/**
	 * Returns a copy of the histogram's hash map.
	 * @return copy of the depth histogram's hash map
	 */
	public HashMap<Integer,Integer> getDepthHistogram(){
		HashMap<Integer,Integer> temp = new HashMap<Integer,Integer>();
				
		lock.lock();		
		try{
			for (int key : dHistogram.keySet())
				temp.put(key, dHistogram.get(key));
		}catch (Exception e){
			System.err.println(e);
		}finally{lock.unlock();}
		
		return temp;
	}
	
	
	/**
	 * Sets the near and far threshold for the depth histogram.
	 * 
	 * @param near - closest distance to camera
	 * @param far  - furthest distance to camera
	 */
	public void setThresholds(int near, int far){
		nearThreshold = near;
		farThreshold = far;
	}
	
	
}
