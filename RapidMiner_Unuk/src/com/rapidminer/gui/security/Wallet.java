/*
 *  RapidMiner
 *
 *  Copyright (C) 2001-2012 by Rapid-I and the contributors
 *
 *  Complete list of developers available at our web site:
 *
 *       http://rapid-i.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package com.rapidminer.gui.security;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.rapidminer.io.Base64;
import com.rapidminer.io.process.XMLTools;
import com.rapidminer.tools.FileSystemService;
import com.rapidminer.tools.GlobalAuthenticator;
import com.rapidminer.tools.I18N;
import com.rapidminer.tools.LogService;
import com.rapidminer.tools.XMLException;

/** The Wallet stores user credentials (username and passwords). It is used by the
 *  {@link GlobalAuthenticator} and can be edited with the {@link PasswordManager}.
 *  {@link UserCredential}s are stored per {@link URL}.
 *   
 *  Note: Though this class has a {@link #getInstance()} method, it is not a pure singleton.
 *  In fact, the {@link PasswordManager} sets a new instance via {@link #setInstance(Wallet)}
 *  after editing.
 *  
 * @author Miguel B�scher
 *
 */
public class Wallet {
	private static final String CACHE_FILE_NAME = "secrets.xml";
	private HashMap<String, UserCredential> wallet = new HashMap<String, UserCredential>();

	private static Wallet instance = new Wallet();
	static {
		instance.readCache();		
	}

	public static Wallet getInstance() {
		return instance;
	}

	public static void setInstance(Wallet wallet) {
		instance = wallet;		
	}

	/** Reads the wallet from the secrets.xml file in the user's home directory. */
	public void readCache() {
		final File userConfigFile = FileSystemService.getUserConfigFile(CACHE_FILE_NAME);
		if (!userConfigFile.exists()) {
			System.err.println("No file exists");
			return;
		}
		//LogService.getRoot().config("Reading secrets file.");
		LogService.getRoot().log(Level.CONFIG, "com.rapidminer.gui.security.Wallet.reading_secrets_file");
		
		Document doc;
		try {
			doc = XMLTools.parse(userConfigFile);
		} catch (Exception e) {
			//LogService.getRoot().log(Level.WARNING, "Failed to read secrets file: "+e, e);
			LogService.getRoot().log(Level.WARNING,
					I18N.getMessage(LogService.getRoot().getResourceBundle(), 
					"com.rapidminer.gui.security.Wallet.reading_secrets_file_error", 
					e),
					e);

			return;
		}
		NodeList secretElems = doc.getDocumentElement().getElementsByTagName("secret");
		UserCredential usercredential;
		for (int i = 0; i < secretElems.getLength(); i++) {			
			Element secretElem = (Element) secretElems.item(i);
			String url = XMLTools.getTagContents(secretElem, "url");
			String user = XMLTools.getTagContents(secretElem, "user");
			char[] password;
			try {
				password = new String(Base64.decode(XMLTools.getTagContents(secretElem, "password"))).toCharArray();				
			} catch (IOException e) {
				//LogService.getRoot().log(Level.WARNING, "Failed to read entry in secrets file: "+e, e);
				LogService.getRoot().log(Level.WARNING,
						I18N.getMessage(LogService.getRoot().getResourceBundle(), 
						"com.rapidminer.gui.security.Wallet.reading_entry_in_secrets_file_error", 
						e),
						e);
				continue;
			}
			usercredential = new UserCredential(url, user, password);
			wallet.put(usercredential.getURL(), usercredential); 
//			System.out.println(wallet.get(usercredential.getURL()));
		}

	}

	public void registerCredentials(UserCredential authentication) {
		wallet.put(authentication.getURL(), authentication);		
	}

	public int size() {
		return wallet.size();
	}

	/** Deep clone. */
	@Override
	public Wallet clone() {
		Wallet clone = new Wallet();
		for (String url : this.getKeys()) {
			UserCredential entry = this.getEntry(url);
			clone.registerCredentials(entry.clone());
		}		
		return clone;
	}
	
	public LinkedList<String> getKeys() {
		Iterator<String> it = wallet.keySet().iterator();
		LinkedList<String> keyset = new LinkedList<String>();
		while (it.hasNext()){
			keyset.add(it.next());
		}
		return keyset;
	}

	public UserCredential getEntry(String url) {
		return wallet.get(url);
	}
	
	public void removeEntry(String url) {
		wallet.remove(url);
	}
	
	/** Saves the wallet to the secrets.xml file in the users home directory. */
	public void saveCache() {
		//LogService.getRoot().config("Saving secrets file.");
		LogService.getRoot().log(Level.CONFIG, "com.rapidminer.gui.security.Wallet.saving_secrets_file");
		Document doc;
		try {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			//LogService.getRoot().log(Level.WARNING, "Failed to create XML document: "+e, e);
			LogService.getRoot().log(Level.WARNING,
					I18N.getMessage(LogService.getRoot().getResourceBundle(), 
					"com.rapidminer.gui.security.Wallet.creating_xml_document_error", 
					e),
					e);
			return;
		}
		Element root = doc.createElement(CACHE_FILE_NAME);
		doc.appendChild(root);
		for (String i : Wallet.getInstance().getKeys()){
			Element entryElem = doc.createElement("secret");
			root.appendChild(entryElem);
			XMLTools.setTagContents(entryElem, "url", i);
			XMLTools.setTagContents(entryElem, "user", wallet.get(i).getUsername());
			XMLTools.setTagContents(entryElem, "password", Base64.encodeBytes(new String(wallet.get(i).getPassword()).getBytes()));
		}
		File file = FileSystemService.getUserConfigFile(CACHE_FILE_NAME);
		try {
			XMLTools.stream(doc, file, null);
		} catch (XMLException e) {
			//LogService.getRoot().log(Level.WARNING, "Failed to save secrets file: "+e, e);
			LogService.getRoot().log(Level.WARNING,
					I18N.getMessage(LogService.getRoot().getResourceBundle(), 
					"com.rapidminer.gui.security.Wallet.saving_secrets_file_error", 
					e),
					e);
		}
	}

	public void remove(String forUrl) {
		wallet.remove(forUrl);		
	}


}
