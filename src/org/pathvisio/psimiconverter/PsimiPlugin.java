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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.bridgedb.BridgeDb;
import org.bridgedb.IDMapper;
import org.bridgedb.IDMapperException;
import org.bridgedb.IDMapperStack;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
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
public class PsimiPlugin {
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
		String pathwayDir = args[0];
		String dbDir = args[1];
		String filename = args[2];
		GpmltoPsimi(pathwayDir, dbDir, filename);
	}

	/**
	 * @param pathwayDirName
	 *            Pathway directory with GPML files
	 * @param dbDir
	 *            Directory containing the gene and metabolite identifier
	 *            mapping database
	 * @param filename
	 *            File to store Psimi interactions
	 */
	public static void GpmltoPsimi(String pathwayDirName, String dbDir,
			String filename) {

		/* Initializing variables */
		File outputFile = new File(filename);
		File pathwayDir = new File(pathwayDirName);
		/* the source contains the organization information */
		Source source = PsiFactory.createSource("WikiPathways");
		/* HashMaps to store information of datanodes */
		/* Identifiers */
		Map<String, String> datanodeIdList = new HashMap<String, String>();
		/* Databases */
		Map<String, String> datanodeDbList = new HashMap<String, String>();
		/* Types */
		Map<String, String> datanodeTypeList = new HashMap<String, String>();
		/* Collection to store Psimi information */
		Collection<Interaction> interactions = new ArrayList<Interaction>();
		Collection<Participant> participants = new ArrayList<Participant>();
		/* Types of interactors */
		InteractorType protInteractorType = PsiFactory
				.createInteractorType("MI:0326", "protein");
		InteractorType metInteractorType = PsiFactory
				.createInteractorType("MI:0328", "small molecule");
		InteractorType interactorType = new InteractorType();
		/* Types of interactions */
		InteractionType interactionType = PsiFactory
				.createInteractionType("MI:0407", "direct interaction");
		/* Create Organism : human */
		Organism human = PsiFactory.createOrganismHuman();
		/*
		 * Experimental information each interaction contains experimental
		 * information, which is contained in the ExperimentDescription object
		 * below
		 */
		InteractionDetectionMethod interactionDetMethod = PsiFactory
				.createInteractionDetectionMethod("MI:0434",
						"phosphatase assay");
		ParticipantIdentificationMethod participantIdentMethod = PsiFactory
				.createParticipantIdentificationMethod("MI:0396",
						"predetermined");
		Organism hostOrganism = PsiFactory.createOrganismInVitro();

		ExperimentDescription experiment1 = PsiFactory.createExperiment(
				"experiment1", "1234567", interactionDetMethod,
				participantIdentMethod, hostOrganism);
		/* Load identifier mapping databases */
		System.out.println("Loading bridge files");
		IDMapperStack loadedGdbs = new IDMapperStack();
		List<File> bridgeFiles = FileUtils.getFiles(new File(dbDir), "bridge",
				true);
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
		/* Loop through all pathways in the pathway directory */
		for (File file : pathwayDir.listFiles()) {
			try {
				Pathway pathway = new Pathway();
				pathway.readFromXml(file, true);
				/*
				 * Get Interactions
				 */
				for (PathwayElement node : pathway.getDataObjects()) {
					if (node.getObjectType() == ObjectType.DATANODE) {
						System.out.println(node.getTextLabel());
						if (!node.getElementID().isEmpty()) {
							datanodeIdList.put(node.getGraphId(),
									node.getElementID());
							datanodeDbList.put(node.getGraphId(), node
									.getDataSource().getFullName());
							datanodeTypeList.put(node.getGraphId(),
									node
									.getDataNodeType());
						}
					}
				}
				System.out.println("IDs = " + datanodeIdList);
				System.out.println("DBs = " + datanodeDbList);
				System.out.println("Types = " + datanodeTypeList);
				for (PathwayElement pwe : pathway.getDataObjects()) {
					if (pwe.getObjectType() == ObjectType.LINE) {
						/*
						 * Get Interactor A Details
						 */
						String startGraphRef = pwe.getStartGraphRef();
						// Source Node
						if (datanodeTypeList.get(startGraphRef)
								.equalsIgnoreCase("Metabolite")) {
							interactorType = metInteractorType;
						} else {
							interactorType = protInteractorType;
						}
						Interactor intA = PsiFactory.createInteractor(
								datanodeIdList.get(startGraphRef),
								datanodeDbList.get(startGraphRef),
								interactorType, human);
						BiologicalRole bioRoleEnzymeTarget = PsiFactory
								.createBiologicalRole("MI:0502",
										"enzyme target");
						ExperimentalRole expRoleNeutral = PsiFactory
								.createExperimentalRole("MI:0497",
										"neutral component");
						Participant participantA = PsiFactory
								.createParticipant(intA,
										bioRoleEnzymeTarget, expRoleNeutral);

						/*
						 * Get Interactor B Details
						 */
						String endGraphRef = pwe.getEndGraphRef();
						// Target Node
						if
						(datanodeTypeList.get(endGraphRef).equalsIgnoreCase(
								"metabolite")) {
							interactorType = metInteractorType;
						} else {
							interactorType = protInteractorType;
						}
						Interactor intB = PsiFactory.createInteractor(
								datanodeIdList.get(endGraphRef),
								datanodeDbList.get(endGraphRef),
								interactorType, human);

						BiologicalRole bioRoleEnzyme = PsiFactory
								.createBiologicalRole(
										datanodeIdList.get(endGraphRef),
										"enzyme");

						Participant participantB = PsiFactory
								.createParticipant(intB, bioRoleEnzyme,
										expRoleNeutral);
						// add the participants to the collection
						participants.add(participantA);
						participants.add(participantB);

						// with all the participants created, an Interaction can
						// now be
						// instantiated
						// Interaction interaction =
						// PsiFactory.createInteraction("interaction1",
						// experiment1, interactionType, participants);

						Interaction interaction = PsiFactory.createInteraction(
								"interaction1", experiment1, interactionType,
								participants);

						// add the interactions to the collection
						interactions.add(interaction);
					}
				}
			} catch (ConverterException e) {
				System.out.println("could not parse pathway from "
						+ file.getAbsolutePath());
			}
		}
		// we put the collection of interactions in an entry
		Entry entry = PsiFactory.createEntry(source, interactions);
		// and finally we create the root object, the EntrySet, that contains
		// the entries
		EntrySet entrySet = PsiFactory.createEntrySet(
				PsimiXmlVersion.VERSION_254, entry);

		// writing a PSI-MI XML file
		// /////////////////////////////////////

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
		System.out.println();
	}
}
