package gui;

import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PConstants;
import toxi.geom.Vec3D;
import toxi.physics.VerletParticle;

/**
 * A simple <a href="http://en.wikipedia.org/wiki/Braitenberg_vehicle">Braitenberg Vehicle</a>
 * that marches along a 3D mesh seeking brighter (and avoiding darker) mesh faces.
 * <br/>
 * Position solving is done on a 2D surface, then mapped to a 3D mesh:
 *  	<ul>- 3D mesh needs to be a ruled surface 			<br/>
 * 			- Each agent has a 2D and 3D representation 	<br/>
 * 		</ul>
 *  
 * @author mad
 */
public class Mycelium {

	
	private World3D p5;
	private Model m;
	private boolean done = false;
	private boolean display = true;
	
	public static int STRUCTURE = 0;
	public static int SKIN = 1;
	private int mode;
	
	private int LENGTH = 100;
	
	private static final int MAXGEN = 3;
	private int currGen = 0;
	
	private static float BRANCH_ANGLE = (float) Math.toRadians(60);
	private float searchAngle = (float) Math.toRadians(30);
	
	private VerletParticle p;	
	ArrayList<Vec3D> path2D = new ArrayList<Vec3D>();
	ArrayList<Vec3D> path3D = new ArrayList<Vec3D>();
	private int id;
	private Vec3D vel;
	private float speed = 0; 
	
	
	// 3D Version of the agent
	private VerletParticle p3D;
	private Vec3D vel3D;

	private float randomBranch = .33f;
	private int branchDelay = 50;
	
	private HeatMap heatMap;
	private int heatMapWidth;
	
	
	
	public Mycelium(World3D p5, Model m, HeatMap heatMap, Vec3D pos, Vec3D vel, float speed, int mode){
		this.p5 = p5;
		this.m = m;
		this.mode = mode;
		
		p = new VerletParticle(pos);
		p.addVelocity(vel);
		
		this.vel = vel;
		this.speed = speed;
		
		this.heatMap = heatMap;
		this.heatMapWidth = heatMap.getWidth();
		
		if (mode == SKIN)
			this.id = m.getSkinAgents().size();
		else
			this.id = m.getStructureAgents().size();
		
		this.p3D = new VerletParticle(heatMap.getFaces().get(get3DIndex()).getCentroid());
		this.vel3D = vel.copy();
		p3D.addVelocity(vel3D);
		
		path2D.add(p.copy());
		path3D.add(p3D.copy());
	}
	
	
	/**
	 * Random movement and branching of Mycelium. 
	 * Used for debugging and to randomly generate a touch-based heatMap.
	 * Phase out!
	 */
	public void grow(){
		
		if (path2D.size() < LENGTH){
			
			
			p.addSelf(vel.scale(speed));	
			p3D.addSelf(vel3D.scale(speed*3)); // <-- may need be a higher speed	

			
			// check that we haven't gone out of bounds 
			if (p.x > heatMapWidth){
				// wrap around the x
				p.x = 0;				
			}	
			else if (p.x < 0){
				p.x = heatMapWidth;
			}
			
			
			// bounce the agent back in bounds
			if (p.y > heatMapWidth - 1 || p.y < 0)
				vel.y *= -2;
							

			// rotate the velocity vector towards a random location
			vel.rotateAroundAxis(Vec3D.Z_AXIS, p5.random(-0.5f, 0.5f) * searchAngle);
			
			// add a bit of weight to the y movement
			vel.y += .05f;
			vel.normalize();
						
			// add to the agent's trail
			path2D.add(p.copy());
			path3D.add(p3D.copy());
			
			
			// use random to trigger a branch event
			if (currGen < MAXGEN && branchDelay < 0 && p5.random(1) < randomBranch)
				branch();
			
			// decrement branch delay
			branchDelay--;
				
			// update the heatMap's colors if it hasn't been generated yet
//			if (!heatMap.isGenerated())
//				heatMap.incrementColor(get2DIndex());

			if (get3DIndex() >= (2*heatMapWidth*heatMapWidth) || get3DIndex() < 0)
				System.err.println("Illegal index in grow(): "+get3DIndex());
			
			else{
				// grab the target face and update the velocity to change heading
				Vec3D target = heatMap.getFaces().get(get3DIndex()).getCentroid();
				target.subSelf(p3D).normalize(); //				<-- Should normalize to a scalar of speed
				vel3D = target.copy();
			}
		}
		else
			done = true;
	}
	
	// temporary debugging variables
	private ArrayList<Vec3D> seekAngles = new ArrayList<Vec3D>(); 
	private Vec3D best2D = new Vec3D();
	private int bestCell  = -1;
	
	/**
	 * Semi-autonomous movement of the 3D agent towards 
	 * the brighter patches of the heatMap.
	 */
	public void seek(){
		
		seekAngles.clear();
		
		if (path2D.size() < LENGTH){
			
			// advance the agent			
			p.addSelf(vel.scale(speed));	
			p3D.addSelf(vel3D.scale(speed*3)); // <-- may need be a higher speed	
			
			int bestColor = -1;			
			for (int i=0; i<6; i++){
							
				// move a test point towards a random location within a field of view
				Vec3D tempP	  = p.copy();
				// use a wider angle so the agent looks at more than one cell
				Vec3D tempVel = vel.copy().rotateZ(p5.random(-0.9f, 0.9f) * searchAngle); 

				float searchSpeed = speed * 10;
				
				// look further ahead that the current speed
				tempP.addSelf(tempVel.scale(searchSpeed));
			
				
				
				// check that we haven't gone out of bounds 
				if (p.x > heatMapWidth){
					// wrap around the x
					p.x = 0;				
				}	
				else if (p.x < 0){
					p.x = heatMapWidth;
				}
				
				// slow as we reach the top or bottom
				if (tempP.y >= heatMapWidth || tempP.y <= 0){

					tempP.interpolateToSelf(p, .5f); 
					speed /= 2;	

				}
				
				
				int index = (int)(tempP.x) + (int)(tempP.y)*heatMapWidth;
				
				if (index >= heatMapWidth*heatMapWidth || index < 0)
					System.err.println("Illegal index in seek(): "+index);
				
				else{
					int color = heatMap.getColor(index);
					if (color > bestColor){
						bestColor = color;
						bestCell = index;
						best2D = tempP.copy();
					}
				}
				seekAngles.add(tempP);
			}		

			
			// update 2D velocity
			Vec3D target = best2D.sub(p).normalize();
			vel = target.copy();

			// add a bit of weight to the y movement
			vel.y += .1f;
			vel.normalize();


			// grab the target face and update the velocity to change heading
			target = heatMap.getFaces().get(bestCell*2).getCentroid();
			target.subSelf(p3D).normalize(); //				<-- Should normalize to a scalar of speed
			vel3D = target.copy();


			// use random to trigger a branch event
			if (currGen < MAXGEN && branchDelay < 0 && bestColor > 10)
				branch();

			// decrement branch delay
			branchDelay--;
			
			

			// add to the agent trails
			path2D.add(p.copy());
			path3D.add(p3D.copy());
		}
		else
			done = true;
		
	}
	
	
	/**
	 * Create a new Mycelium as an off-chute of its parent.
	 */
	private void branch(){	
		
		Vec3D velocity = vel.copy().rotateAroundAxis(Vec3D.Z_AXIS, p5.random(-0.5f, 0.5f) * BRANCH_ANGLE);
	            
		Mycelium agent = new Mycelium(p5, m, heatMap, p, velocity, speed*1.1f, mode);
		
		this.currGen += 1;
		agent.currGen = this.currGen;
		
		if (mode == SKIN)
			m.getSkinAgents().add(agent);
		
		else
			m.getStructureAgents().add(agent);
		
		branchDelay = 25;
		
	}


	/**
	 * Draw the agent, its heading, and its trail.
	 */
//	public void display(){
//	
//		// draw trail
//		for (int i=0; i<path2D.size()-1; i++){
//			Vec3D v0 = path2D.get(i);
//			Vec3D v1 = path2D.get(i+1);
//			Vec3D v2 = path3D.get(i);
//			Vec3D v3 = path3D.get(i+1);
//			
//			// avoid drawing a wrapped line
////			if (v0.distanceToSquared(v1) < 50*50){
//				
////				p5.pushMatrix();
////				p5.translate(heatMapWidth, 0);
////				p5.strokeWeight(1);	
////				p5.line(v0.x,v0.y,v0.z,v1.x,v1.y,v1.z);
////				p5.popMatrix();
//				
////				p5.strokeWeight(3);
//				p5.line(v2.x,v2.y,v2.z,v3.x,v3.y,v3.z);
////			}
//		}
//		
//		p5.pushStyle();
//		
//		p5.noFill();
//		p5.stroke(250,10,250,50);
//		p5.pushMatrix();
//		p5.translate(heatMapWidth, 0);	// offset when 2D, so the 3D mesh doesn't cover it
//		
//		// DEBUGGING SEEKING
//		if (heatMap.isGenerated() && bestCell > -1){
//		
//			p5.stroke(10,245,230);
//			p5.strokeWeight(1);
//			for (Vec3D v : seekAngles)
//				p5.line(v.x,v.y,v.z,p.x,p.y,p.z);
//	
//			// show the target vector 2D
//			p5.stroke(255,0,230);		
//			p5.line(best2D.x,best2D.y,best2D.z,p.x,p.y,p.z);
//		
//		}
//		
//		if (display){
//			p5.noStroke();
//			p5.fill(2,124,232);		
//			// draw agent
//			p5.ellipseMode(PConstants.CENTER);
//			p5.ellipse(p.x, p.y, 1f, 1f);
//			p5.strokeWeight(2);
//			p5.stroke(0,250,0);
//			Vec3D p0 = p.copy(); 
//			p0.addSelf(vel.scale(speed));
////			p5.line(p.x,p.y,p.z,p0.x,p0.y,p0.z);
//			p5.line(vel.x+p0.x,vel.y+p0.y,vel.z+p0.z,p0.x,p0.y,p0.z);
//		}
//		
//		p5.popMatrix();
//		
//		p5.popStyle();
//		
//	}
	
	private int get2DIndex(){
		int x = (int) p.x;
		int y = (int) p.y;
		
		return x + y * heatMapWidth;
	}
	
	public int get3DIndex(){		
		return get2DIndex()* 2;
	}
	

	public void setMaxLength(int num){
		LENGTH = num;
	}
	
	
	public boolean isDone(){
		return done;
	}
	
	public Vec3D getPos(){
		return p;
	}
	
	public Vec3D getVel(){
		return vel;
	}
	
	public float getSpeed(){
		return speed;
	}

}
