package gui;

import java.util.ArrayList;

import toxi.geom.Vec3D;
import toxi.geom.mesh.TriangleMesh;
import toxi.physics.VerletPhysics;

public class Model {

	private World3D p5;
	private Arm arm;
	private VerletPhysics physics;
	
	// MESH 
	private ArrayList<ArrayList<Vec3D>> outerPts = new ArrayList<ArrayList<Vec3D>>();
	private ArrayList<ArrayList<Vec3D>> innerPts = new ArrayList<ArrayList<Vec3D>>();
	private TriangleMesh mesh = new TriangleMesh();
	
	
	// GESTURE FLAGS
	private boolean touching = false;
	private boolean scaling  = false;
	private boolean pinching = false;
	
	private Vec3D touchPos = new Vec3D();
	
	private boolean isInitialized = false;
	
	
	
	
	public Model(World3D p5, Arm arm) {
		this.p5 = p5;
		this.arm = arm;
		this.physics = p5.getPhysics();		
	}
	
	public void update(){

		ArrayList<Vec3D> wEdge = arm.getWristEdge();
		ArrayList<Vec3D> eEdge = arm.getElbowEdge();
		
		// if we have valid edges
		if (wEdge.size() > 0 && eEdge.size() > 0){
			float scalar = 1.2f;
			outerPts = generateSrf(wEdge, eEdge, 5, scalar);
			mesh = buildInnerMesh();
			mesh.addMesh(buildOuterMesh());
			mesh.addMesh(buildEdgeMesh());
		}
		
	}
	
	/**
	 * Builds a standard mesh along the canvas area of the arm.
	 * 
	 * @return inner mesh of model
	 */
	private TriangleMesh buildInnerMesh() {
		
		TriangleMesh temp = new TriangleMesh();		
		Vec3D v0, v1, v2, v3;
		

		for (int i=0; i<innerPts.size()-1; i++){
			
			for (int j=0; j<innerPts.get(i).size(); j++){
				
				if (j == innerPts.get(1).size()-1){					
					v0 = innerPts.get(i).get(j);
					v1 = innerPts.get(i).get(0);
					v2 = innerPts.get(i+1).get(0);	
					v3 = innerPts.get(i+1).get(j);				
				}
				
				else{
					v0 = innerPts.get(i).get(j);
					v1= innerPts.get(i).get(j+1);
					v2 = innerPts.get(i+1).get(j+1);	
					v3 = innerPts.get(i+1).get(j);
				}
				
				temp.addFace(v0,v1,v2);				
				temp.addFace(v2,v3,v0);
		
			}
		}		
		
		return temp;
	}
	
	/**
	 * Builds a dynamic mesh with animate SrfModules.
	 * 
	 * @return outer mesh of model
	 */
	private TriangleMesh buildOuterMesh() {

		TriangleMesh temp = new TriangleMesh();		
		Vec3D v0, v1, v2, v3;


		for (int i=0; i<outerPts.size()-1; i++){

			for (int j=0; j<outerPts.get(i).size(); j++){

				if (j == outerPts.get(1).size()-1){					
					v0 = outerPts.get(i).get(j);
					v1 = outerPts.get(i).get(0);
					v2 = outerPts.get(i+1).get(0);	
					v3 = outerPts.get(i+1).get(j);				
				}

				else{
					v0 = outerPts.get(i).get(j);
					v1 = outerPts.get(i).get(j+1);
					v2 = outerPts.get(i+1).get(j+1);	
					v3 = outerPts.get(i+1).get(j);
				}

				temp.addFace(v0,v1,v2);				
				temp.addFace(v2,v3,v0);

			}

		}


		return temp;
	}
	
	/**
	 * Builds the edges that connect the inner and outer meshes
	 * 
	 * @return edges of the model
	 */
	private TriangleMesh buildEdgeMesh() {
		
		TriangleMesh temp = new TriangleMesh();		
		Vec3D v0, v1, v2, v3;

		ArrayList<Vec3D> w0 = innerPts.get(0);
		ArrayList<Vec3D> w1 = outerPts.get(0);
		ArrayList<Vec3D> e0 = innerPts.get(innerPts.size()-1);
		ArrayList<Vec3D> e1 = outerPts.get(outerPts.size()-1);
		
		// connect edges
		for (int i=0; i<w0.size(); i++){
			
			if (i == w0.size()-1){
				v0 = w0.get(i);
				v1 = w0.get(0);
				v2 = w1.get(i);
				v3 = w1.get(0);
			}
			else{
				v0 = w0.get(i);
				v1 = w0.get(i+1);
				v2 = w1.get(i);
				v3 = w1.get(i+1);
			}
			
			temp.addFace(v2,v1,v0);
			temp.addFace(v3,v1,v2);
				
			if (i == w0.size()-1){
				v0 = e0.get(i);
				v1 = e0.get(0);
				v2 = e1.get(i);
				v3 = e1.get(0);
			}
			else{
				v0 = e0.get(i);
				v1 = e0.get(i+1);
				v2 = e1.get(i);
				v3 = e1.get(i+1);
			}
			
			temp.addFace(v0,v1,v2);
			temp.addFace(v2,v1,v3);			
		}
		
		return temp;
	}

	private ArrayList<ArrayList<Vec3D>> generateSrf(ArrayList<Vec3D> wEdge, ArrayList<Vec3D> eEdge, int res, float offset) {
		
		ArrayList<ArrayList<Vec3D>> pts = new ArrayList<ArrayList<Vec3D>>();
		// add the first section
		pts.add(wEdge);		
		for (int i=0; i<res; i++){

			float t = i/(1.0f*res);
			ArrayList<Vec3D> section = new ArrayList<Vec3D>();

			for (int j=0; j<wEdge.size(); j++){

				Vec3D w0 = wEdge.get(j);
				Vec3D e0 = eEdge.get(j);

				float x = (e0.x - w0.x) * t + w0.x;
				float y = (e0.y - w0.y) * t + w0.y;
				float z = (e0.z - w0.z) * t + w0.z;

				section.add(new Vec3D(x,y,z));

			}
			pts.add(section);
		}
		// add the last section
		pts.add(eEdge);


		// set the current points as the inner surface
		innerPts.clear();

		for (ArrayList<Vec3D> list : pts){
			ArrayList<Vec3D> section = new ArrayList<Vec3D>();

			Vec3D centroid = new Vec3D();
			centroid.addSelf(list.get(0));
			centroid.addSelf(list.get(pts.get(0).size()/4));
			centroid.addSelf(list.get(pts.get(0).size()/2));
			centroid.addSelf(list.get(3*pts.get(0).size()/4));
			centroid.scaleSelf(.25f);

			// translation vector for scaling outer edges
			Vec3D trans = centroid.scale(offset);
			trans.subSelf(centroid);

			for (Vec3D v : list){
				// save the old point for the inner surface
				section.add(new Vec3D(v));			
				// scale for the outer surface
				v.scaleSelf(offset).subSelf(trans);
			}
			innerPts.add(section);
		}
	
		return pts;
	}
	
	
	public TriangleMesh getMesh(){
		return mesh;
	}
	
}
