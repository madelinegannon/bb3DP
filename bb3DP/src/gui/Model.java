package gui;

import java.util.ArrayList;

import processing.core.PConstants;
import toxi.geom.Vec3D;
import toxi.geom.mesh.Face;
import toxi.geom.mesh.TriangleMesh;
import toxi.geom.mesh.Vertex;
import toxi.physics.VerletPhysics;

public class Model {

	private World3D p5;
	private Arm arm;
	private VerletPhysics physics;
	
	// MESH 
	private ArrayList<ArrayList<Vec3D>> outerPts = new ArrayList<ArrayList<Vec3D>>();
	private ArrayList<ArrayList<Vec3D>> innerPts = new ArrayList<ArrayList<Vec3D>>();
	private TriangleMesh mesh = new TriangleMesh();
	
	private ArrayList<Face> faces3D = new ArrayList<Face>();				// holds an ordered list of the mesh faces
	ArrayList<Mycelium> structure = new ArrayList<Mycelium>(); 
	private ArrayList<Mycelium> skin      = new ArrayList<Mycelium>();
	
	HeatMap heatMap;
	
	// GESTURE FLAGS
	private boolean touching = false;
	private boolean scaling  = false;
	private boolean pinching = false;
	
	private Vec3D touchPos = new Vec3D();
	
	private boolean isInitialized = false;
	private boolean isGenerated = false;
	
	
	
	public Model(World3D p5, Arm arm) {
		this.p5 = p5;
		this.arm = arm;
		this.physics = p5.getPhysics();		
	}
	
	public void update(){

		ArrayList<Vec3D> wEdge = arm.getWristEdge();
		ArrayList<Vec3D> eEdge = arm.getElbowEdge();	
		
		
		int res = 60;
		
		// if we have valid edges, rebuild the mesh
		if (wEdge.size() > 0 && eEdge.size() > 0){
			float scalar = 1.1f;
			outerPts = generateSrf(wEdge, eEdge, res, scalar);
//			mesh = buildInnerMesh();
//			System.out.println("outer mesh starts at: "+mesh.getNumVertices());
			mesh = buildOuterMesh();
//			mesh.addMesh(buildOuterMesh());
//			System.out.println("outer mesh ends at  : "+mesh.getNumVertices());
//			mesh.addMesh(buildEdgeMesh());
		}
		
		if (!isInitialized){
			heatMap = new HeatMap(p5, faces3D, 2*res);
			
//			System.out.println("wEdge: "+arm.getWristEdge().size());
//			System.out.println("res  : "+res);	
//			System.out.println("cells: "+heatMap.getCells().size());
//			System.out.println("faces: "+mesh.getNumFaces());
//			System.out.println("verts: "+mesh.getNumVertices());
//			System.out.println();			
			
			isInitialized = true;
		}
		
		
		
		if (heatMap != null && faces3D.size() != 0 && heatMap.getFaces() != null){			
			
			// update the heatMap
			heatMap.update(faces3D);
			displayHeatMap();
					
			// update the agents
			for (int i=0; i<skin.size(); i++){
				Mycelium m = skin.get(i);			
				m.grow();
			}
			for (int i=0; i<structure.size(); i++){
				Mycelium m = structure.get(i);
				m.seek();
			}
			
			displayAgents(true, true, false);
			
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
		
		// clear our old mesh faces
		faces3D.clear();
		

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
					v1 = innerPts.get(i).get(j+1);
					v2 = innerPts.get(i+1).get(j+1);	
					v3 = innerPts.get(i+1).get(j);
				}
				
				temp.addFace(v0,v1,v2);				
				temp.addFace(v2,v3,v0);
		
				if (i!= 0){ // <-- why aren't faces3D and temp.getNumFaces the same size???? 
					int id = i*innerPts.size() + j;
					
					Face f0 = new Face(new Vertex(v0, id), new Vertex(v1, id+1),new Vertex(v2, id+2));				
					Face f1 = new Face(new Vertex(v2, id+2),new Vertex(v3, id+3),new Vertex(v0, id+0));				
					faces3D.add(f0);
					faces3D.add(f1);

				}
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

		// clear our old mesh faces
		faces3D.clear();

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
				
				if (i!= 0){ // <-- why aren't faces3D and temp.getNumFaces the same size???? 
					int id = i*outerPts.size() + j;
					
					Face f0 = new Face(new Vertex(v0, id), new Vertex(v1, id+1),new Vertex(v2, id+2));				
					Face f1 = new Face(new Vertex(v2, id+2),new Vertex(v3, id+3),new Vertex(v0, id+0));				
					faces3D.add(f0);
					faces3D.add(f1);

				}
				

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
	
	public void reset(){
		heatMap.reset();
	}
	
	/**
	 * Creates a new set of agents to crawl across the 3D mesh.
	 * 
	 * The <i>structure</i> agents seek the brighter parts of the heatmap, and
	 * the <i>skin</i> agents wander randomly.
	 */
	private void initAgents(){
		
//		step = false;
//		simDone = false;
//		showMesh = true;
		
		skin.clear();
		structure.clear();
		

		int repeat = 3;
		for (int i=0; i<repeat; i++){
//			int res = 10;
//			for (int j=0; j<heatMap.getWidth()/res; j++)		
//				skin.add(new Mycelium(p5, this, heatMap, new Vec3D(j*res,0,0), new Vec3D(0,1,0), .25f, Mycelium.SKIN));
			int res = 5;
			 for (int j=0; j<heatMap.getWidth()/res; j++)
				structure.add(new Mycelium(p5, this, heatMap, new Vec3D(j*res,0,0), new Vec3D(0,1.2f,0), .25f, Mycelium.STRUCTURE));
			
		}

	}
	
	public void generate(){
		initAgents();
		isGenerated = true;
	}

	public ArrayList<Mycelium> getSkinAgents() {
		return skin;
	}
	
	public ArrayList<Mycelium> getStructureAgents() {
		return structure;
	}
	
	public void displayHeatMap(){
		if (p5.showMesh)
			heatMap.display();
	}
	
	public void displayAgents(boolean show2D, boolean showStructure, boolean showSkin){
		
		if (showStructure){

			for (Mycelium m : structure){
//				m.display();
				// draw trail
				for (int i=0; i<m.path2D.size()-1; i++){

					if (show2D){
						Vec3D v0 = m.path2D.get(i);
						Vec3D v1 = m.path2D.get(i+1);
						p5.pushMatrix();
						p5.translate(50, 0);
						p5.strokeWeight(1);	
						if (v0.distanceToSquared(v1) < (heatMap.getWidth()/2)*(heatMap.getWidth()/2))
							p5.line(v0.x,v0.y,v0.z,v1.x,v1.y,v1.z);
						p5.popMatrix();
						
//						p5.noStroke();
//						p5.fill(2,124,232);		
//						// draw agent
//						p5.ellipseMode(PConstants.CENTER);
//						p5.ellipse(m.getPos().x, m.getPos().y, 1f, 1f);
//						p5.strokeWeight(2);
//						p5.stroke(0,250,0);
//						Vec3D p0 = m.getPos().copy(); 
//						p0.addSelf(m.getVel().scale(m.getSpeed()));
//						p5.line(m.getVel().x+p0.x,m.getVel().y+p0.y,m.getVel().z+p0.z,p0.x,p0.y,p0.z);
					}
					
					// show 3D version
					p5.noFill();
					p5.stroke(12,190,232);
					Vec3D v2 = m.path3D.get(i);
					Vec3D v3 = m.path3D.get(i+1);
					p5.line(v2.x,v2.y,v2.z,v3.x,v3.y,v3.z);

				}

			}
			
		}
		
		
		if (showSkin){		
			
			for (Mycelium m : skin){
//				m.display();
				// draw trail
				for (int i=0; i<m.path2D.size()-1; i++){

					if (show2D){
						Vec3D v0 = m.path2D.get(i);
						Vec3D v1 = m.path2D.get(i+1);
						p5.pushMatrix();
						p5.translate(50, 0);
						p5.strokeWeight(1);	
						if (v0.distanceToSquared(v1) < (heatMap.getWidth()/2)*(heatMap.getWidth()/2))
							p5.line(v0.x,v0.y,v0.z,v1.x,v1.y,v1.z);
						p5.popMatrix();
						
//						p5.noStroke();
//						p5.fill(2,124,232);		
//						// draw agent
//						p5.ellipseMode(PConstants.CENTER);
//						p5.ellipse(m.getPos().x, m.getPos().y, 1f, 1f);
//						p5.strokeWeight(2);
//						p5.stroke(0,250,0);
//						Vec3D p0 = m.getPos().copy(); 
//						p0.addSelf(m.getVel().scale(m.getSpeed()));
////						p5.line(p.x,p.y,p.z,p0.x,p0.y,p0.z);
//						p5.line(m.getVel().x+p0.x,m.getVel().y+p0.y,m.getVel().z+p0.z,p0.x,p0.y,p0.z);
					}
					
					// show 3D version
					p5.noFill();
					p5.stroke(180);
					Vec3D v2 = m.path3D.get(i);
					Vec3D v3 = m.path3D.get(i+1);
					p5.line(v2.x,v2.y,v2.z,v3.x,v3.y,v3.z);

				}
			}
			
		}
		
	}
	
}
