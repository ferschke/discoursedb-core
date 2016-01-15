package edu.cmu.cs.lti.discoursedb.annotation.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.cmu.cs.lti.discoursedb.annotation.model.AnnotationInterchange;
import edu.cmu.cs.lti.discoursedb.configuration.BaseConfiguration;
import edu.cmu.cs.lti.discoursedb.core.model.annotation.AnnotationInstance;
import edu.cmu.cs.lti.discoursedb.core.model.macro.Content;
import edu.cmu.cs.lti.discoursedb.core.model.macro.Contribution;
import edu.cmu.cs.lti.discoursedb.core.model.macro.Discourse;
import edu.cmu.cs.lti.discoursedb.core.service.annotation.AnnotationService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.ContributionService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.DiscourseService;

/**
 * This class exports annotations on Contribution entities for a given discourse. The
 * annotations in the json can be edited offline or by a third party software
 * and then be imported back into the DiscourseDB database.
 * 
 * @author Oliver Ferschke
 */
@Component
@SpringBootApplication
@ComponentScan(	basePackages = { "edu.cmu.cs.lti.discoursedb.configuration", "edu.cmu.cs.lti.discoursedb.annotation.io" }, 
				useDefaultFilters = false, 
				includeFilters = {@ComponentScan.Filter(
						type = FilterType.ASSIGNABLE_TYPE, 
						value = {SimpleContributionAnnotator.class, BaseConfiguration.class })})
public class SimpleContributionAnnotator implements CommandLineRunner {

	private static final Logger logger = LogManager.getLogger(SimpleContributionAnnotator.class);	
	
	@Autowired private DiscourseService discourseService;
	@Autowired private ContributionService contribService;
	@Autowired private AnnotationService annoService;
	
	private static String discourseName;
	private static String outputFileName;
	
	/**
	 * Launches the SpringBoot application 
	 * 
	 * @param args 
	 */
	public static void main(String[] args) {
		if(args.length!=1){
        	throw new IllegalArgumentException("USAGE: SimpleContributionAnnotator <DiscourseName>");
		}
		discourseName = args[0];
        SpringApplication.run(SimpleContributionAnnotator.class, args);       
	}
	
	@Override
	@Transactional
	public void run(String... args) throws Exception {
		List<AnnotationInterchange> output = new ArrayList<>();		
		Optional<Discourse> existingDiscourse = discourseService.findOne(discourseName);
		if(!existingDiscourse.isPresent()){
			logger.warn("Discourse with name "+discourseName+" does not exist.");
			return;
		}		

		for(Contribution curContrib: contribService.findAllByDiscourse(existingDiscourse.get())){
			Content curContent = curContrib.getCurrentRevision();
			
			/*
			 * Get existing annotations
			 */
			Set<AnnotationInstance> existingContribAnnos = annoService.findAnnotations(curContrib);
			logger.info(existingContribAnnos.size()+" annotations on contribution");
			
			Set<AnnotationInstance> existingContentAnnos = annoService.findAnnotations(curContent);
			logger.info(existingContentAnnos.size()+" annotations on content");
			
			/*
			 * Create new annotations
			 */
			//for contribution
			AnnotationInstance newContribAnno = new AnnotationInstance();

			//for content
			AnnotationInstance newContentAnno = new AnnotationInstance();
			
			
			/*
			 * Annotate: save annotations and link to entities 
			 */
			annoService.addAnnotation(curContrib,newContribAnno);
			annoService.addAnnotation(curContent,newContentAnno);
			
		}		
	}
}
