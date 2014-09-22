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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;

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

import psidev.psi.mi.xml.PsimiXmlVersion;
import psidev.psi.mi.xml.PsimiXmlWriter;
import psidev.psi.mi.xml.PsimiXmlWriterException;
import psidev.psi.mi.xml.model.BiologicalRole;
import psidev.psi.mi.xml.model.Entry;
import psidev.psi.mi.xml.model.EntrySet;
import psidev.psi.mi.xml.model.ExperimentDescription;
import psidev.psi.mi.xml.model.ExperimentalRole;
import psidev.psi.mi.xml.model.Interaction;
import psidev.psi.mi.xml.model.InteractionDetectionMethod;
import psidev.psi.mi.xml.model.InteractionType;
import psidev.psi.mi.xml.model.Interactor;
import psidev.psi.mi.xml.model.InteractorType;
import psidev.psi.mi.xml.model.Organism;
import psidev.psi.mi.xml.model.Participant;
import psidev.psi.mi.xml.model.ParticipantIdentificationMethod;
import psidev.psi.mi.xml.model.PsiFactory;
import psidev.psi.mi.xml.model.Source;

/**
 * @author anwesha
 *
 */
public class PsimiPlugin implements PathwayExporter {
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
	/* Collection to store Psimi information */
	Collection<Interaction> interactions = new ArrayList<Interaction>();
	Collection<Participant> participants = new ArrayList<Participant>();
	/* Roles */
	BiologicalRole bioRole = PsiFactory.createBiologicalRole("MI:0499",
			"unspecified role");
	ExperimentalRole expRole = PsiFactory.createExperimentalRole("MI:0499",
			"unspecified role");
	InteractorType interactorType = new InteractorType();
	/* Types of interactions */
	InteractionType interactionType = PsiFactory.createInteractionType(
			"MI:0407", "direct interaction");
	InteractionType complexinteractionType = PsiFactory.createInteractionType(
			"MI:0195", "covalent binding");
	/* Create Organism : human */
	Organism human = PsiFactory.createOrganismHuman();
	/*
	 * Experimental information each interaction contains experimental
	 * information, which is contained in the ExperimentDescription object below
	 */
	InteractionDetectionMethod interactionDetMethod = PsiFactory
			.createInteractionDetectionMethod("MI:0363", "inferred by author");
	ParticipantIdentificationMethod participantIdentMethod = PsiFactory
			.createParticipantIdentificationMethod("MI:0363",
					"inferred by author");
	ExperimentDescription experiment = PsiFactory.createExperiment(
			"experiment1", "1234567", interactionDetMethod,
			participantIdentMethod, human);

	/**
	 * Modified example script available at
	 * https://code.google.com/p/psimi/source
	 * /browse/trunk/psimi-examples/src/main
	 * /java/org/hupo/psi/mi/example/xml/CreateXml.java
	 * 
	 * @author anwesha
	 * @author Bruno Aranda (baranda@ebi.ac.uk)
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("Started");
		String pathwayDirName = args[0];
		String dbDirName = args[1];
		String filename = args[2];
		PsimiPlugin psimi = new PsimiPlugin();
		psimi.loadIdMappers(dbDirName);
		File pathwayDir = new File(pathwayDirName);
		for (File file : pathwayDir.listFiles()) {
			psimi.getNodeInfo(psimi.openPathway(file));
			psimi.getComplexInfo(psimi.openPathway(file));
		}
		psimi.savePsimi(filename);
	}

	private void savePsimi(String filename) {
		File outputFile = new File(filename);
		/* we put the collection of interactions in an entry */
		Entry entry = PsiFactory.createEntry(source, interactions);
		/*
		 * and finally we create the root object, the EntrySet, that contains
		 * the entries
		 */
		EntrySet entrySet = PsiFactory.createEntrySet(
				PsimiXmlVersion.VERSION_254, entry);

		/* writing a PSI-MI XML file */
		PsimiXmlWriter psimiXmlWriter = new PsimiXmlWriter(
				PsimiXmlVersion.VERSION_254);
		Writer fileWriter;
		try {
			fileWriter = new FileWriter(outputFile);
			psimiXmlWriter.write(entrySet, fileWriter);
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (psidev.psi.mi.xml.converter.ConverterException e) {
			e.printStackTrace();
		} catch (JAXBException e) {
			e.printStackTrace();
		} catch (PsimiXmlWriterException e) {
			e.printStackTrace();
		}
		System.out.println("Finished!");
	}

	private Pathway openPathway(File pathwayFile) {
		Pathway pathway = new Pathway();
		try {
			pathway.readFromXml(pathwayFile, true);
		} catch (ConverterException e) {
			e.printStackTrace();
		}
		return pathway;
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
				if (!node.getElementID().isEmpty()) {
					datanodeIdList.put(node.getGraphId(), node.getElementID());
					datanodeDbList.put(node.getGraphId(), node.getDataSource()
							.getSystemCode());
					datanodeTypeList.put(node.getGraphId(),
							node.getDataNodeType());
				}
			}
		}
		convertInter(pwy);
	}

	private void convertInter(Pathway pwy) {
		for (PathwayElement pwe : pwy.getDataObjects()) {
			if (pwe.getObjectType() == ObjectType.LINE) {
				/*
				 * Line is connected to nodes on both sides
				 */
				if (!(pwe.getStartGraphRef().isEmpty() && pwe.getEndGraphRef()
						.isEmpty())) {

					String startGraphRef = pwe.getStartGraphRef();
					String endGraphRef = pwe.getEndGraphRef();
					/*
					 * Get Interactor A Details
					 */
					System.out.println("start = " + startGraphRef);
					if (!(datanodeTypeList.get(startGraphRef) == null || datanodeTypeList
							.get(endGraphRef) == null)) {
						interactorType = determineNodeType(datanodeTypeList
								.get(startGraphRef));
						// Get Prefered ID
						Xref reftoUse = getPrefferedId(
								datanodeTypeList.get(startGraphRef),
								datanodeIdList.get(startGraphRef),
								datanodeDbList.get(startGraphRef));
						Interactor intA = PsiFactory.createInteractor(reftoUse
								.getId(), reftoUse.getDataSource()
								.getFullName(), interactorType, human);
						Participant participantA = PsiFactory
								.createParticipant(intA, bioRole, expRole);
						/*
						 * Get Interactor B Details
						 */

						// Target Node
						interactorType = determineNodeType(datanodeTypeList
								.get(endGraphRef));
						// Get Prefered ID
						reftoUse = getPrefferedId(
								datanodeTypeList.get(endGraphRef),
								datanodeIdList.get(endGraphRef),
								datanodeDbList.get(endGraphRef));
						Interactor intB = PsiFactory.createInteractor(reftoUse
								.getId(), reftoUse.getDataSource()
								.getFullName(), interactorType, human);
						Participant participantB = PsiFactory
								.createParticipant(intB, bioRole, expRole);
						// add the participants to the collection
						participants.add(participantA);
						participants.add(participantB);
						/*
						 * with all the participants created, an Interaction can
						 * now be instantiated
						 */
						Interaction interaction = PsiFactory.createInteraction(
								"interaction1", experiment, interactionType,
								participants);

						// add the interactions to the collection
						interactions.add(interaction);
					}

				}
			}
		}
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
					complexTypeMap.add(pwe.getDataNodeType());
					complexIdMap.add(pwe.getElementID());
					complexDbMap.add(pwe.getDataSource()
							.getSystemCode());
				}
			}
			convertComplexToInteractions();
		}
	}

	private void convertComplexToInteractions() {
		for (int i = 0; i < complexTypeMap.size(); i++) {
			/*
			 * Get Interactor A Details [Start from Beginning of map]
			 */
			//			System.out.println("start = " + startGraphRef);
			if (!(complexTypeMap.get(i) == null || complexTypeMap.get(i) == null)) {
				interactorType = determineNodeType(complexTypeMap
						.get(i));
				// Get Prefered ID
				Xref reftoUse = getPrefferedId(
						complexTypeMap.get(i),
						complexIdMap.get(i), complexDbMap.get(i));
				Interactor intA = PsiFactory.createInteractor(reftoUse
						.getId(), reftoUse.getDataSource()
						.getFullName(), interactorType, human);
				Participant participantA = PsiFactory
						.createParticipant(intA, bioRole, expRole);
				// add the participants to the collection
				participants.add(participantA);
			}
			for (int j = complexTypeMap.size() - 1; j >= 0; j--) {
				/*
				 * Get Interactor B Details [Start from End of map]
				 */

				// Target Node
				interactorType = determineNodeType(complexTypeMap
						.get(j));
				// Get Prefered ID
				Xref reftoUse = getPrefferedId(
						complexTypeMap.get(j),
						complexIdMap.get(j), complexDbMap.get(j));
				Interactor intB = PsiFactory.createInteractor(reftoUse
						.getId(), reftoUse.getDataSource()
						.getFullName(), interactorType, human);
				Participant participantB = PsiFactory
						.createParticipant(intB, bioRole, expRole);
				participants.add(participantB);
			}
			/*
			 * with all the participants created, an Interaction can
			 * now be instantiated
			 */
			Interaction interaction = PsiFactory.createInteraction(
					"interaction1", experiment, complexinteractionType,
					participants);

			// add the interactions to the collection
			interactions.add(interaction);
		}
	}

	private static Xref getPrefferedId(String type, String id, String db) {
		if (db == null) {
			db = "ck";
		}
		DataSource origds = DataSource.getBySystemCode(db);
		Xref Ref = new Xref(id, origds);
		try {
			System.out.println(Ref + " : check if xref exists = "
					+ loadedGdbs.xrefExists(Ref));
		} catch (IDMapperException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("Original Xref=" + Ref);
		DataSource prefds;
		if (type.equalsIgnoreCase("Metabolite")) {
			prefds = DataSource.getBySystemCode("Ce");
		} else if (type.equalsIgnoreCase("Protein")) {
			prefds = DataSource.getBySystemCode("S");
		} else {
			prefds = DataSource.getBySystemCode("En");
		}

		if (!origds.equals(prefds)) {
			System.out.println("Pref ds=" + prefds.getFullName());
			// Lookup the cross-references for the wanted database code
			try {
				Set<Xref> newRefs = loadedGdbs.mapID(Ref, prefds);
				if (!newRefs.isEmpty()) {
					// System.out.println(newRefs.toArray());
					Ref = newRefs.iterator().next();
					System.out.println(Ref + " : check if xref exists = "
							+ loadedGdbs.xrefExists(Ref));

				} else {
					System.out.println(Ref + " : check if xref exists = "
							+ loadedGdbs.xrefExists(Ref));
				}

			} catch (IDMapperException e) {
				e.printStackTrace();
			}
		}


		return Ref;
	}

	private InteractorType determineNodeType(String datanodeType) {
		System.out.println(datanodeType);
		if (datanodeType.equalsIgnoreCase("Metabolite")) {
			interactorType = PsiFactory.createInteractorType("MI:0328",
					"small molecule");
		} else if (datanodeType.equalsIgnoreCase("Protein")) {
			interactorType = PsiFactory.createInteractorType("MI:0326",
					"protein");
		} else if (datanodeType.equalsIgnoreCase("GeneProduct")) {
			interactorType = PsiFactory.createInteractorType("MI:0250", "gene");
		} else {
			interactorType = PsiFactory.createInteractorType("MI:0329",
					"unknown participant");
		}
		return interactorType;
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
		return new String[] { "xml" };
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
