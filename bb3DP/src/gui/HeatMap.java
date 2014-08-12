package gui;

import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PConstants;
import toxi.geom.Vec3D;
import toxi.geom.mesh.Face;

/**
 * Interactive heat map of arm touches.
 * 
 * @author mad
 */
public class HeatMap {

	private World3D p5;
	
	/**
	 * An ordered list of 3D mesh faces
	 */
	private ArrayList<Face> faces3D;
	
	/**
	 * An ordered list of the 2D grid cells.
	 * 2 mesh faces make 1 grid cell.
	 */
	private ArrayList<Cell> cells2D;
	
	/**
	 * An ordered list of face colors to be shared between 2D and 3D calculations
	 */
	private ArrayList<Integer> faceColors = new ArrayList<Integer>();
	
	private int maxColor = 0;
	
	/**
	 * With of a mesh row. Used to transform 1D into 2D array.
	 */
	private int meshWidth;
	
	private boolean isGenerated = false;
	private boolean isInitialized = false;

	
	public HeatMap(World3D p5, ArrayList<Face> faces3D, int meshWidth){
		this.p5 = p5;
		this.faces3D = faces3D;
		this.meshWidth = meshWidth;

	}
	

	public void update(ArrayList<Face> faces3D){
		this.faces3D = faces3D;		
		
		if (!isInitialized)
			cells2D = generate2DGrid();
	}
	
	/**
	 * Creates a 2D grid for mapping interactions to the 3D faces.	<br/><br/>
	 * 
	 * The 2D grid uses QUADS instead of the TRIANGLE faces of the faces3D, so
	 * there are 1/2 the number of grid cells than faces.
	 * 
	 * @return a 2D grid of cells from [(0,0), (meshWidth/2, 0), (meshWidth/2, meshWidth), (0, meshWidth)]
	 */
	private ArrayList<Cell> generate2DGrid() {

		ArrayList<Cell> temp = new ArrayList<Cell>();
		
		for (int i=0; i<faces3D.size(); i+=2){
			
			int y = i / meshWidth;
			int x = i % meshWidth / 2; // dividing by 2 removes spacing
			
			Vec3D a = new Vec3D(x,y,0);
			Vec3D b = new Vec3D(x+1,y,0);
			Vec3D c = new Vec3D(x+1,y+1,0);
			Vec3D d = new Vec3D(x,y+1,0);
			
			Vec3D[] quad = new Vec3D[4];
			
			quad[0] = a; quad[1] = b; quad[2] = c; quad[3] = d;

			temp.add(new Cell(quad));
			
			faceColors.add(0);
				
			isInitialized = true;
		}
		
		return temp;
	}
	
	/**
	 * Makes a cell brighter when an agent has crawled on it.
	 * 
	 * @param index
	 */
	public void incrementColor(int index){
		
		if (index >= faceColors.size() || index < 0)
			System.err.println("Illegal index in incrementColor(): "+ index);

		else{

			// increment the color
			int col = faceColors.get(index)+1;
			faceColors.set(index, PApplet.min(col,255));
			
			// mirror bilaterally
			
			int y = index / getWidth();
			int x = index % getWidth();
			index = Math.abs(x - getWidth()) + y * getWidth();
			faceColors.set(index, PApplet.min(col,255));
			
			if (col > maxColor){
				maxColor = col;				
			}

			for (int i=0; i<cells2D.size(); i++){					
				cells2D.get(i).color = (int) PApplet.map(faceColors.get(i), 0, maxColor, 0, 255);
			}		
		}
		
//		blur();		
	}
	

	/**
	 * Standard 3x3 blur of the face colors.
	 */
	public void blur(){
		
		for (int i=getWidth()+1; i<cells2D.size()-getWidth()-1; i++){	
			
			int right, left, top, btm, topL, topR, btmL, btmR;
			
			// get our column
			int y = i / meshWidth;
			
			// get our center cell color
			int col = cells2D.get(i).color;
			
			top = i-getWidth();
			btm = i+getWidth();
			
			// if we are at the left edge, wrap the the right edge
			if (i == y*getWidth())
				left = y*getWidth() - 1;
			else
				left = i-1;
			
			// if we are at the right edge, wrap to the left edge <-- MAYBE DON'T NEED TO DO THIS?
			if (i == y*getWidth() - 1)
				right = y*getWidth();
			else
				right = i+1;
			
			btmR = right+getWidth();
			topR = right-getWidth();
			btmL = left+getWidth();
			topL = left-getWidth();
						
			col += cells2D.get(top).color;
			col += cells2D.get(btm).color;
			col += cells2D.get(right).color;
			col += cells2D.get(left).color;
			col += cells2D.get(btmR).color;
			col += cells2D.get(btmL).color;
			col += cells2D.get(topR).color;
			col += cells2D.get(topL).color;
			
			
			// average values
			col = PApplet.min(col/7, 255); // use a smaller divisor to weight the color brighter

			// update cell2D
			cells2D.get(i).color = col;
			faceColors.set(i, col);
		}	
		
	}
	

	public void display(){

		p5.pushStyle();
		p5.pushMatrix();
		p5.translate(50, 0);
		for (Cell c : cells2D)
			c.display();	
		p5.popMatrix();
		
		p5.noStroke();
		
		// visualize 3D 
		for (int i=0; i<cells2D.size(); i++){
			
			Face f = faces3D.get(i*2);
			int color = cells2D.get(i).color;
			p5.fill(color);
			
			p5.beginShape(PConstants.TRIANGLE);			
			p5.vertex(f.a.x,f.a.y,f.a.z);
			p5.vertex(f.b.x,f.b.y,f.b.z);
			p5.vertex(f.c.x,f.c.y,f.c.z);
			p5.endShape();
			
			f = faces3D.get(i*2+1);
			p5.beginShape(PConstants.TRIANGLE);			
			p5.vertex(f.a.x,f.a.y,f.a.z);
			p5.vertex(f.b.x,f.b.y,f.b.z);
			p5.vertex(f.c.x,f.c.y,f.c.z);
			p5.endShape();
			
		}
		p5.popStyle();
	}
	
	int faceIndex = 0;
	void set(int index, int color){
		cells2D.get(index).color = color;
		faceIndex = index;
//		System.out.println("updated cell "+index+" with color: "+color);
	}
	
	public void reset(){

		for (int i=0; i<faceColors.size(); i++){
			faceColors.set(i,0);
			cells2D.get(i).color = 0;
		}
			
	}
	
	/**
	 * Gets actual width of heatMap.
	 * 
	 * @return number of columns for heatMap
	 */
	public int getWidth(){
		return meshWidth / 2;
	}
	
	/**
	 * Returns the 3D faces of the mesh
	 * @return
	 */
	public ArrayList<Face> getFaces(){
		return faces3D;
	}
	
	/**
	 * Returns the 2D cells of the mesh
	 * @return
	 */
	public ArrayList<Cell> getCells(){
		return cells2D;
	}
	
	public int getColor(int index){
		return cells2D.get(index).color;
	}

	public boolean isGenerated(){
		return isGenerated;
	}
	
	public void setIsGenerated(boolean flag){
		isGenerated = flag;
	}
	
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	
	/**
	 * Not really working ....
	 * Adapted from Jos Stam's <a href="http://www.intpowertechcorp.com/GDC03.pdf">Real-Time Fluid Dynamics</a>
	 */
	private void diffuse(){
		
		float rate = .4f * .0003f * getWidth() * getWidth();
//		System.out.println("rate: "+rate);

		
		int [] colors = new int[cells2D.size()];
		for (int k=0; k<20; k++){

			int maxColor = Integer.MIN_VALUE;
			int minColor = Integer.MAX_VALUE;
			
			for (int i=getWidth()+1; i<cells2D.size()-getWidth()-1; i++){
				
				int right, left, top, btm;
				int y = i / meshWidth;

				top = i-getWidth();
				btm = i+getWidth();

				// if we are at the left edge, wrap the the right edge
				if (i == y*getWidth())
					left = y*getWidth() - 1;
				else
					left = i-1;

				// if we are at the right edge, wrap to the left edge <-- MAYBE DON'T NEED TO DO THIS?
				if (i == y*getWidth() - 1)
					right = y*getWidth();
				else
					right = i+1;

				/*
				 * color = currCol + diffusionRate * (top,btm,left,right colors) / (1+4*diffusionRate);
				 */
				int color = (int)(cells2D.get(i).color + 
						rate * (cells2D.get(top).color + 
								cells2D.get(btm).color + 
								cells2D.get(right).color + 
								cells2D.get(left).color) / (1+4*rate) );
				
				if (color > maxColor)
					maxColor = color;
				
				if (color < minColor)// && color > 5)
					minColor = color;
				
				
//				if (color != 0){
//					System.out.println(i+"'s color was : "+cells2D.get(i).color+", but is now: "+color);
//					System.out.println(i+", cells2D.get(i).color ;"+cells2D.get(i).color);
//					System.out.println(i+", cells2D.get(top).color ;"+cells2D.get(top).color);
//					System.out.println(i+", cells2D.get(top).color ;"+cells2D.get(top).color);
//					System.out.println(i+", cells2D.get(top).color ;"+cells2D.get(top).color);
//				}
//				cells2D.get(i).color = color; 
				colors[i] = color;
			}
//			System.out.println();
			
			for (int i=0; i<cells2D.size(); i++){
//				if (colors[i] > 1)
					cells2D.get(i).color = (int) PApplet.map(colors[i], minColor, maxColor, 0, 255);
					
			}
		}
	}
	

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	
	/**
	 * A grid cell of the 2D surface that emulates the 3D mesh.
	 * 
	 * Basically, it's just a quad with a color assignment
	 * 
	 * @author mad
	 */
	private class Cell{
		
		private Vec3D[] quad = new Vec3D[4];
		private int color = 0;
	
		public Cell(Vec3D[] pts){
			quad = pts;
		}
		
		public void display(){
			p5.pushStyle();
			
			p5.fill(color);
			p5.stroke(50);
			p5.strokeWeight(1);
			
			p5.beginShape(PConstants.QUAD);
			p5.vertex(quad[0].x, quad[0].y, quad[0].z);
			p5.vertex(quad[1].x, quad[1].y, quad[1].z);
			p5.vertex(quad[2].x, quad[2].y, quad[2].z);
			p5.vertex(quad[3].x, quad[3].y, quad[3].z);			
			p5.endShape();
			
			p5.popStyle();
		}
	}
}
