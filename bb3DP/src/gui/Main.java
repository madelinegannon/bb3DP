package gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Main extends Frame{

	private World3D world;
	private DepthCAM dCAM;
	GridBagConstraints c;
	
	public Main(){

		super("Body-Based 3D Printing");	
		
		//get display screen size for Frame sizing
		Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		Dimension preferredSize = new Dimension(2300, 970);

		this.setResizable(true);
		this.setPreferredSize(preferredSize);
		this.setMaximumSize(screenSize);
		this.setMinimumSize(preferredSize);

		this.setBackground(new Color(50,50,50));
		this.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		
		// instantiate the Applet
		world = new World3D(this); 
		dCAM  = new DepthCAM(this);
		
		// 3D WORLD
		c.fill = GridBagConstraints.VERTICAL;
		c.insets  = new Insets(10,10,10,10);
		c.gridx = 0;
		c.gridwidth = 1;
		c.gridheight = 2;
		c.gridy = 0;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		// add the PApplet to the frame
		this.add(world,c);

		// DEPTH CAM
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets  = new Insets(0,10,10,10);
		c.gridx = 1;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.gridy = 1;
		c.anchor = GridBagConstraints.SOUTHEAST;
		// add the PApplet to the frame
		this.add(dCAM,c);

		pack();

		// closes application 
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			} });


		world.init();
		dCAM.init();

		}
	
	public static void main(String[] args) {

		new Main().setVisible(true);
	
	}
	
	
	public World3D get3D(){
		return world;
	}
	
	

}
