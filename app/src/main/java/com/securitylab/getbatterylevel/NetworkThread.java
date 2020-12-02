package com.securitylab.getbatterylevel;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

public class NetworkThread extends Thread {
	private static final String GOOGLE_DNS_SERVER = "8.8.8.8";
	private static final int SERVER_PORT = 53;
	private Timer networkTimer;
	private DatagramSocket s;
	private InetAddress server;

	public NetworkThread()
	{
		super("NetworkThread");
	}
	
	public void run()
	{
		try {
			s = new DatagramSocket();
			server = InetAddress.getByName(GOOGLE_DNS_SERVER);
		} catch (Exception e) {
			e.printStackTrace();
		}
		createNetworkTimer();
	}

	private void createNetworkTimer () {
		TimerTask networkTimerTask = new TimerTask() {
			@Override
			public void run() {
				byte[] message = new byte[1000];
				try {
					DatagramPacket p = new DatagramPacket(message, 1000, server, SERVER_PORT);
					s.send(p);
					Log.d(Constants.TAG, "send data package at: " + System.currentTimeMillis());
				} catch (Exception e) {
					Log.d(Constants.TAG, e.getMessage());
					e.printStackTrace();
				}
			}
		};
		networkTimer = new Timer();
		networkTimer.scheduleAtFixedRate(networkTimerTask, getStartDelay(), 1000);
	}

	public long getStartDelay() {
		long currentTime = System.currentTimeMillis();
		long startTime = ((System.currentTimeMillis() / 10000) + 1) * 10000;
		return (startTime - currentTime);
	}

	public void quit()
	{
		networkTimer.cancel();
	}
}