package edu.cmu.cs.lti.discoursedb.annotation.demo.io;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.cmu.cs.lti.discoursedb.configuration.BaseConfiguration;

/**
 * This class imports annotations on Contribution entities for a given discourse. 
 * 
 * @author Oliver Ferschke
 */
@Component
@SpringBootApplication
@ComponentScan(	basePackages = { "edu.cmu.cs.lti.discoursedb.configuration", "edu.cmu.cs.lti.discoursedb.annotation.demo.io" }, 
useDefaultFilters = false, 
includeFilters = {@ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE, 
		value = {ContributionBinaryLabelImporter.class, BaseConfiguration.class })})
public class ContributionBinaryLabelImporter implements CommandLineRunner {
	/**
	 * Launches the SpringBoot application 
	 * 
	 * @param args 
	 */
	public static void main(String[] args) {
        SpringApplication.run(ContributionBinaryLabelImporter.class, args);       
	}
	
	@Override
	@Transactional
	public void run(String... args) throws Exception {
		
	}
}
