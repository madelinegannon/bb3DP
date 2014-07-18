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

	private GUI_3D gui;
	private GUI_DepthCAM dcam;
	private GUI_Histogram dHist;
	
	public Main(){

		super("On Body 3D Modeling - demo_forearm");		
		
		//get display screen size for Frame sizing
		Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		Dimension preferredSize = new Dimension(screenSize.width/2+180, screenSize.height/2);

		this.setResizable(true);
		this.setPreferredSize(preferredSize);
		this.setMaximumSize(screenSize);
		this.setMinimumSize(preferredSize);
		
		// instantiate the Applet
		gui = new GUI_3D(this); 
		dcam = new GUI_DepthCAM(this);
		dHist = new GUI_Histogram(this);

		this.setBackground(new Color(50,50,50));
		this.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		
		c.fill = GridBagConstraints.VERTICAL;
		c.insets  = new Insets(10,10,10,5);
		c.gridx = 0;
		c.gridwidth = 4;
		c.gridheight = 2;
		c.gridy = 0;
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		// add the PApplet to the frame
		this.add(gui,c);
		
//		this.setLayout(new FlowLayout(FlowLayout.CENTER));
//		this.add(gui);
//		this.add(dcam);
		
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets  = new Insets(10,10,5,10);
		c.gridx = 4;
		c.gridy = 0;
		c.anchor = GridBagConstraints.FIRST_LINE_END;
		// add the PApplet to the frame
		this.add(dcam,c);
		
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets  = new Insets(10,10,10,10);
		c.gridx = 4;
		c.gridy = 1;
		c.anchor = GridBagConstraints.LAST_LINE_END;
		// add the PApplet to the frame
		this.add(dHist,c);
		
		
		pack();


		// closes application 
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			} });

		gui.init();
		dcam.init();
		dHist.init();
//		repaint();
		}
	
	public static void main(String[] args) {

		new Main().setVisible(true);
		
		
	}

	public GUI_3D get3D(){
		return gui;
	}
	
	public GUI_Histogram getHistogram(){
		return dHist;
	}
	
	public GUI_DepthCAM getDepthCAM(){
		return dcam;
	}
}
