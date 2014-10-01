/**
 * Copyright 2009 The European Bioinformatics Institute, and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pathvisio.psimiconverter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bridgedb.BridgeDb;
import org.bridgedb.DataSource;
import org.bridgedb.IDMapper;
import org.bridgedb.IDMapperException;
import org.bridgedb.IDMapperStack;
import org.bridgedb.Xref;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.GroupStyle;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.model.PathwayExporter;
import org.pathvisio.core.util.FileUtils;

import psidev.psi.mi.xml.model.PsiFactory;
import psidev.psi.mi.xml.model.Source;

/**
 * @author anwesha
 * 
 */
public class Interactions implements PathwayExporter {

	/* the source contains the organization information */
	Source source = PsiFactory.createSource("WikiPathways");
	/* HashMaps to store information of datanodes */
	Map<String, String> datanodeIdList;
	Map<String, String> datanodeDbList;
	Map<String, String> datanodeTypeList;
	List<String> complexIdList;
	List<String> complexIdMap;
	List<String> complexDbMap;
	List<String> complexTypeMap;
	/* IDMappers */
	static IDMapperStack loadedGdbs;
	List<String> interactions;

	/**
	 * Modified example script available at
	 * https://code.google.com/p/psimi/source
	 * /browse/trunk/psimi-examples/src/main
	 * /java/org/hupo/psi/mi/example/xml/CreateXml.java
	 * 
	 * @author anwesha
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String interaction = "";
		System.out.println("Started");
		String pathwayDirName = args[0];
		String dbDirName = args[1];
		String filename = args[2];
		Interactions psimi = new Interactions();
		psimi.loadIdMappers(dbDirName);
		File outputFile = new File(filename);
		outputFile.createNewFile();
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		File pathwayDir = new File(pathwayDirName);
		for (File file : pathwayDir.listFiles()) {
			Pathway pathway = new Pathway();
			pathway.readFromXml(file, true);
			psimi.getNodeInfo(pathway);
			writer.write("Source\tNodeType\tTarget\tNodeType\tInteractionType\n");
			for (PathwayElement pwe : pathway.getDataObjects()) {
				if (pwe.getObjectType() == ObjectType.LINE) {
					interaction = psimi.convertInter(pwe);
					System.out.println(interaction);
					writer.write(interaction);
				}
			}
			psimi.getComplexInfo(pathway);
			psimi.convertComplexToInteractions(writer);

		}
		writer.flush();
		writer.close();
		System.out.println("Finished!");
	}

	private void getNodeInfo(Pathway pwy) {
		datanodeIdList = new HashMap<String, String>();
		datanodeDbList = new HashMap<String, String>();
		datanodeTypeList = new HashMap<String, String>();
		/*
		 * Get Data Nodes
		 */
		for (PathwayElement node : pwy.getDataObjects()) {
			if (node.getObjectType() == ObjectType.DATANODE) {
				// System.out.println(node.getTextLabel());
				if (!(node.getElementID().isEmpty() || node.getDataSource() == null)) {
					datanodeIdList.put(node.getGraphId(), node.getElementID());
					datanodeDbList.put(node.getGraphId(), node.getDataSource()
							.getSystemCode());
					datanodeTypeList.put(node.getGraphId(),
							node.getDataNodeType());
				}
			}
		}

	}

	private String convertInter(PathwayElement pwe)
	{
		String interaction = "";
		/*
		 * Line is connected to nodes on both sides
		 */
		if (!(pwe.getStartGraphRef() == null && pwe.getEndGraphRef() == null)) {
			String startGraphRef = pwe.getStartGraphRef();
			String endGraphRef = pwe.getEndGraphRef();

			if (!(datanodeTypeList.get(startGraphRef) == null || datanodeTypeList
					.get(endGraphRef) == null)) {
				Xref sourceReftoUse = getPrefferedId(
						datanodeTypeList.get(startGraphRef),
						datanodeIdList.get(startGraphRef),
						datanodeDbList.get(startGraphRef));
				Xref targetReftoUse = getPrefferedId(
						datanodeTypeList.get(endGraphRef),
						datanodeIdList.get(endGraphRef),
						datanodeDbList.get(endGraphRef));
				interaction = sourceReftoUse + "\t"
						+ datanodeTypeList.get(startGraphRef)
						+ "\t" + targetReftoUse + "\t"
						+ datanodeTypeList.get(endGraphRef) + "\t"
						+ pwe.getEndLineType() + "\n";
			}
		}
		return interaction;
	}

	private void getComplexInfo(Pathway pwy) {
		complexIdList = new ArrayList<String>();
		for (PathwayElement pwe : pwy.getDataObjects()) {
			if (pwe.getObjectType() == ObjectType.GROUP
					&& pwe.getGroupStyle() == GroupStyle.COMPLEX
					&& pwe.getGroupId() != null) {
				System.out.println("GroupID = " + pwe.getGroupId());
				System.out.println("complexes = " + complexIdList);
				complexIdList.add(pwe.getGroupId());
			}
		}
		System.out.println("complexes = " + complexIdList);
		if (complexIdList != null) {
			getComplexComponents(pwy);
		}
	}

	private void getComplexComponents(Pathway pwy) {
		complexTypeMap = new ArrayList<String>();
		complexIdMap = new ArrayList<String>();
		complexDbMap = new ArrayList<String>();
		for (String complexId : complexIdList) {
			System.out.println("complexID" + complexId);
			for (PathwayElement pwe : pwy.getDataObjects()) {
				/*
				 * Getting complex components
				 */
				if (pwe.getObjectType() == ObjectType.DATANODE
						&& pwe.getGroupRef() != null
						&& pwe.getGroupRef().equalsIgnoreCase(complexId)) {
					System.out.println("GroupRef = " + pwe.getGroupRef());
					if (!(pwe.getElementID().isEmpty() || pwe.getDataSource() == null)) {
						complexTypeMap.add(pwe.getDataNodeType());
						complexIdMap.add(pwe.getElementID());
						complexDbMap.add(pwe.getDataSource().getSystemCode());
					}
				}
			}

		}
	}

	private void convertComplexToInteractions(BufferedWriter writer)
			throws IOException {
		String interaction = "";
		for (int i = 0; i < complexTypeMap.size(); i++) {
			/*
			 * Get Interactor A Details [Start from Beginning of map]
			 */
			Xref sourceReftoUse = getPrefferedId(complexTypeMap.get(i),
					complexIdMap.get(i), complexDbMap.get(i));

			for (int j = complexTypeMap.size() - 1; j > 1; j--) {
				Xref targetReftoUse = getPrefferedId(complexTypeMap.get(j),
						complexIdMap.get(j), complexDbMap.get(j));

				interaction = sourceReftoUse + "\t" + complexTypeMap.get(i)
						+ "\t" + targetReftoUse + "\t" + complexTypeMap.get(j)
						+ "\t" + "Covalent Binding" + "\n";
				writer.write(interaction);
			}
		}

	}

	private static Xref getPrefferedId(String type, String id, String db) {
		if (db == null) {
			db = "ck";
		}
		DataSource origds = DataSource.getBySystemCode(db);
		Xref Ref = new Xref(id, origds);

		DataSource prefds;
		if (type.equalsIgnoreCase("Metabolite")) {
			prefds = DataSource.getBySystemCode("Ce");
		} else if (type.equalsIgnoreCase("Protein")) {
			prefds = DataSource.getBySystemCode("S");
		} else {
			prefds = DataSource.getBySystemCode("En");
		}

		if (!origds.equals(prefds)) {

			// Lookup the cross-references for the wanted database code
			try {
				Set<Xref> newRefs = loadedGdbs.mapID(Ref, prefds);
				if (!newRefs.isEmpty()) {
					// System.out.println(newRefs.toArray());
					Ref = newRefs.iterator().next();
				}

			} catch (IDMapperException e) {
				e.printStackTrace();
			}
		}
		return Ref;
	}

	private void loadIdMappers(String dbDirName) {
		/* Load identifier mapping databases */
		System.out.println("Loading bridge files");
		loadedGdbs = new IDMapperStack();
		List<File> bridgeFiles = FileUtils.getFiles(new File(dbDirName),
				"bridge", true);
		for (File dbFile : bridgeFiles) {
			try {
				Class.forName("org.bridgedb.rdb.IDMapperRdb");
				IDMapper gdb = BridgeDb.connect("idmapper-pgdb:" + dbFile);
				loadedGdbs.addIDMapper(gdb);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IDMapperException e) {
				e.printStackTrace();
			}
			System.out.println("Bridge file: " + dbFile.getName() + " loaded.");
		}
	}

	@Override
	public String getName() {
		return "Interactions list";
	}

	@Override
	public String[] getExtensions() {
		return new String[] { "txt" };
	}

	@Override
	public List<String> getWarnings() {
		return Collections.emptyList();
	}

	@Override
	public void doExport(File file, Pathway pathway) throws ConverterException {
		// TODO Auto-generated method stub

	}
}
