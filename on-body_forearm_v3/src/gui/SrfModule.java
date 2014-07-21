package gui;

import java.util.ArrayList;

import toxi.geom.Vec3D;
import toxi.physics.VerletParticle;
import toxi.physics.VerletPhysics;
import toxi.physics.VerletSpring;
import toxi.physics.behaviors.AttractionBehavior;

public class SrfModule {
	
	private VerletPhysics physics;
	private Vec3D start, normal;
	
	private ArrayList<Integer> indices = new ArrayList<Integer>();
	
	public SrfModule(VerletPhysics physics, Vec3D start, Vec3D normal){
		this.physics = physics;
		this.start = start;
		this.normal = normal;
		
		createSpine(3,.01f, 10, -1);
		
	}

	/**
	 * 
	 * @param res		- how many particles in the spine
	 * @param stiffness	- soft/stiff springs
	 * @param repulsion - repulsion force of each head
	 */
	private void createSpine(int res, float stiffness, int radius, int repulsion) {
		
		// create a chain of res particles from the anchor along the normal
		for (int i=0; i<res; i++){
				
			if (i>0)
				normal.normalizeTo(res*i);
			VerletParticle p = new VerletParticle(start);
			p.addSelf(normal);
				
			// remeber the indices into the physics world
			int index = physics.particles.size();
			indices.add(index);
			
			physics.addParticle(p);
			
			// add repulsion behavior at the head
			if (i == res-1 || i == 0){
				p.setWeight(res);
//				physics.addBehavior(new AttractionBehavior(p,3*radius,repulsion));
			}
//			else
//				physics.addBehavior(new AttractionBehavior(p,radius,repulsion/2));
			
			
			// connect via springs
			if (i>0){
				VerletParticle p0 = physics.particles.get(index-1);
				physics.addSpring(new VerletSpring(p,p0,p0.distanceTo(p),stiffness));
			}
			
		}

		
	}
	

	/**
	 * Update the position of the locked particles.
	 *  particles.
	 */
	public void update(Vec3D anchor){
		physics.particles.get(getTail()).x = anchor.x;
		physics.particles.get(getTail()).y = anchor.y;
		physics.particles.get(getTail()).z = anchor.z;
	}
	
	/**
	 * Returns the particle index of the module's head
	 * @return - head index
	 */
	public int getHead(){
		return indices.get(indices.size()-1);
	}
	
	/**
	 * Returns the particle index of the module's tail (anchored to the mesh)
	 * @return - tail index
	 */
	public int getTail(){
		return indices.get(0);
	}
	
	/**
	 * Returns the particle indices of the entire module
	 * @return - particle indices
	 */
	public ArrayList<Integer> getIndices(){
		return indices;		
	}

}
