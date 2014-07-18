package gui;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import processing.core.PApplet;
import processing.core.PFont;
import toxi.geom.Vec3D;
import SimpleOpenNI.SimpleOpenNI;

public class GUI_Histogram extends PApplet{

	
	private Frame parent;
	private int width,height;
	private boolean initialized = false;
	
	private DepthHistogram dHistogram;
	private HashMap<Integer,Integer> avgDepthHist = new HashMap<Integer,Integer>();
	private ArrayList<HashMap<Integer,Integer>> depthHists = new ArrayList<HashMap<Integer,Integer>>(); 
	private int smoothing = 100;
	private int dHistCounter = 0;
	
	private int minValue = Integer.MAX_VALUE;
	private int maxValue = Integer.MIN_VALUE;
	
	private int nearThreshold = 450;
	private int farThreshold = 700;
	private int span = 1;
	private int bins = Math.round((farThreshold-nearThreshold)/span)+1;
	
	private PFont font;
	
	private Lock lock = new ReentrantLock();
	
	public GUI_Histogram(Frame parent){
		this.parent = parent;
		this.width  = 640;
		this.height = 300;		
	}
	
		
	public void setup(){
		size(this.width, this.height);
		background(0);
		
		font = loadFont("CourierNewPSMT-18.vlw");
		textFont(font, 12);
		
		// create DepthHistogram
		dHistogram = new DepthHistogram(nearThreshold, farThreshold,span,bins);
	}
	
	public void draw(){
		background(0);
		smooth();
		ellipse(mouseX,mouseY,10,10);
			
		if (initialized){

			// remember the last n-sets of depth Histograms
			if (depthHists.size() < smoothing)
				depthHists.add(dHistogram.getDepthHistogram());
			else
				depthHists.set(dHistCounter%smoothing, dHistogram.getDepthHistogram());
			dHistCounter++;

//				println(depthHists.get(0).keySet().size());

			if (dHistCounter > smoothing){

//					println(depthHists.get(0).equals(depthHists.get(1)));

				averageDepthHists();
				findMinMaxCount();
				drawAveragedHist();
			}
		}
		
		
		// show framerate
		pushStyle();
		fill(255);
		text(frameRate, 10, 22);
		popStyle();
		

	}
	
	/**
	 * Finding the average depth histogram
	 */
	private void averageDepthHists(){
		lock.lock();
		avgDepthHist.clear();
		
		// build bins
		for (int i=0; i<bins+1; i++){
			avgDepthHist.put((int) (i*span),0);	
		}
				
		try{
			for (int i=0; i<smoothing; i++){
				HashMap<Integer,Integer> depth = depthHists.get(i);
				for (int key : depth.keySet()){
					
					if (!avgDepthHist.containsKey(key))
						avgDepthHist.put(key, depth.get(key));
					else
						avgDepthHist.put(key, avgDepthHist.get(key)+depth.get(key));
					
					// after we've added everything, average it out
					if (i == smoothing - 1){
						avgDepthHist.put(key, avgDepthHist.get(key)/smoothing);
//						println("");
//						println("original value - "+(key+nearThreshold)+":"+depth.get(key));
//						println("averaged value - "+(key+nearThreshold)+":"+avgDepthHist.get(key));
					}
				}
//				println("----------------------------");
			}
			}catch (Exception e){
				System.err.println(e);
			}finally{lock.unlock();}
		
		
		
	}
	


	private void drawAveragedHist(){
		
		// for mapping depth coordinates
		int xOffset = 10;
		int yOffset = 10;
				
				
		lock.lock();
		try{				
			for(int i : avgDepthHist.keySet()){

				int xPos = (int) map(i+nearThreshold,nearThreshold,bins*span+nearThreshold,xOffset,width-xOffset);
				int yPos = (int) map(avgDepthHist.get(i), minValue, maxValue, height-yOffset,30);
				
				// draw 0 values in the background
				if (avgDepthHist.get(i) == 0){
					stroke(65,125,127,50);
					line(xPos,height-yOffset,xPos,0);
				}
				
				// highlight the peak depth
				if (avgDepthHist.get(i) == maxValue){
					pushStyle();
					text(nearThreshold + i,xPos+10,yPos+5);
					fill(108,232,202,200);
					strokeWeight(2);
					ellipse(xPos,yPos,10,10);
					popStyle();
				}
				
				// draw depth thresholds
				if (xPos == width-xOffset){
					pushStyle();
					stroke(65,125,127,150);
					strokeWeight(2);
					// draw farThreshold
					line(width-xOffset,height-yOffset,width-xOffset,30);
					text(nearThreshold + i, width-xOffset-30, 55); // should be far thresh
					popStyle();
				}
				
				if (xPos == xOffset){
					pushStyle();
					stroke(65,125,127,150);
					strokeWeight(2);
					// draw nearThreshold
					line(xOffset,height-yOffset,xOffset,30);
					text(nearThreshold + i, xOffset+5, 55);
					popStyle();
				}
				
				// draw intermittent values
				if (i/span == bins/2 || i/span == bins/4 || i/span == 3*bins/4){
					pushStyle();
					text(nearThreshold + i,xPos-10,yPos-10);
					stroke(54,247,255);
					line(xPos,height-yOffset,xPos,yPos);
					fill(42,255,143,100);
					ellipse(xPos,yPos,10,10);	
					popStyle();
				}
				
				// draw regular values
				else{
					stroke(131,250,255);
					line(xPos,height-yOffset,xPos,yPos);
				}
			}						
		}catch (Exception e){
			System.err.println(e);
		}finally{lock.unlock();}
	}
	
	
	
	/**
	 * Called by DepthCAM once the kinect has setup.
	 * 
	 * @param context - SimpleOpenNI object
	 */
	public void update(SimpleOpenNI context){
		
		initialized = true;
		
		dHistogram.update(context);
	}
	


	/**
	 * Gets the min/max counts per bin
	 */
	private void findMinMaxCount(){
		
		minValue = Integer.MAX_VALUE;
		maxValue = Integer.MIN_VALUE;
		
		lock.lock();		
		try{
			for (int c: avgDepthHist.values()){
				
				if (c < minValue)
					minValue = c;
				if (c > maxValue)
					maxValue = c;
			}
		}catch (Exception e){
			System.err.println(e);
		}finally{lock.unlock();}
		
		
//		System.out.println("minValue: "+minValue);
//		System.out.println("maxValue: "+maxValue);
		
	}
	
	
	public boolean isInitalized(){
		return initialized;
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
