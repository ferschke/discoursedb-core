/*******************************************************************************
 * Copyright (C)  2015 - 2016  Carnegie Mellon University
 * Author: Oliver Ferschke
 *
 * This file is part of DiscourseDB.
 *
 * DiscourseDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * DiscourseDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DiscourseDB.  If not, see <http://www.gnu.org/licenses/> 
 * or write to the Free Software Foundation, Inc., 51 Franklin Street, 
 * Fifth Floor, Boston, MA 02110-1301  USA
 *******************************************************************************/
package edu.cmu.cs.lti.discoursedb.io.wikipedia.article.converter;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.wikipedia.api.DatabaseConfiguration;
import de.tudarmstadt.ukp.wikipedia.api.Page;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants.Language;
import de.tudarmstadt.ukp.wikipedia.api.Wikipedia;
import de.tudarmstadt.ukp.wikipedia.api.hibernate.WikiHibernateUtil;
import de.tudarmstadt.ukp.wikipedia.revisionmachine.api.Revision;
import de.tudarmstadt.ukp.wikipedia.revisionmachine.api.RevisionApi;
import de.tudarmstadt.ukp.wikipedia.revisionmachine.api.RevisionIterator;
import edu.cmu.cs.lti.discoursedb.core.model.macro.Discourse;
import edu.cmu.cs.lti.discoursedb.core.model.macro.DiscoursePart;
import edu.cmu.cs.lti.discoursedb.core.service.macro.ContentService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.ContributionService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.DiscoursePartService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.DiscourseService;
import edu.cmu.cs.lti.discoursedb.core.type.DiscoursePartTypes;
import edu.cmu.cs.lti.discoursedb.io.wikipedia.article.model.ContextTransactionData;

/**
 * This class is invoked by the WikipediaContextArticleConverterApplication.
 * 
 * The converter imports discussion contexts, i.e. the changes in the articles while discussions where happening.
 * NOTE: The database might get really big since every revision is stored in full.
 * 
 * To achieve this, you have to have a DiscourseDB database with imported Talk pages.
 * The converter then retrieves all DiscourseParts of the TALK_PAGE type.
 * For each TALK PAGE DP, it determines the time of the first and last discussion that is associated with the Talk page.
 * It then retrieves all revisions of the associated article within this time window. These article revisions are mapped as content entities associated with a context entity.
 * Each contribution to the discussion is linked to the context entity.
 * 
 * In case no revision activity was recorded within the discussion window, the singe latest article revision is retrieved that was current when the discussion were going on.
 * No context is created for TALK PAGE entities without any contributions.
 * 
 * Usage: WikipediaContextArticleConverterApplication <DB_HOST> <DB> <DB_USER> <DB_PWD> <LANGUAGE>
 * 
 * @author Oliver Ferschke
 *
 */
@Component
public class WikipediaContextArticleConverter implements CommandLineRunner {

	private static final Logger logger = LogManager.getLogger(WikipediaContextArticleConverter.class);

	@Autowired
	private DiscoursePartService discoursePartService;
	@Autowired
	private WikipediaContextArticleConverterService converterService;
	@Autowired
	private ContributionService contribService;
	@Autowired
	private DiscourseService discourseService;
	@Autowired
	private ContentService contentService;

	@Override
	public void run(String... args) throws Exception {
		if (args.length != 5) {
			throw new RuntimeException("Incorrect number of launch parameters.");
		}

		logger.trace("Establishing connection to Wikipedia db...");
		DatabaseConfiguration dbconf = new DatabaseConfiguration();
		dbconf.setHost(args[0]);
		dbconf.setDatabase(args[1]);
		dbconf.setUser(args[2]);
		dbconf.setPassword(args[3]);
		dbconf.setLanguage(Language.valueOf(args[4]));
		Wikipedia wiki = new Wikipedia(dbconf);
		RevisionApi revApi = new RevisionApi(dbconf);

		List<DiscoursePart> talkPageDPs = discoursePartService.findAllByType(DiscoursePartTypes.TALK_PAGE);
		logger.info("Start mapping context articles for " + talkPageDPs.size() + " existing Talk pages");
		int curContextNumber = 1;		
		for (DiscoursePart curTalkPageDP : talkPageDPs) {
			logger.info("Mapping context "+(curContextNumber++)+" of "+talkPageDPs.size()+" for " + curTalkPageDP.getName());

			// Get reference to the article for the given Talk page
			// Move on to the next Talk page if the article cannot be retrieved.
			Page article = null;
			try{
				article=wiki.getPage(curTalkPageDP.getName());				
			}catch(Exception e){
				logger.error("Error retrieving article "+curTalkPageDP.getName());
				continue;
			}
			int articleId = article.getPageId();

			ContextTransactionData contextTransactionData =  converterService.mapContext(curTalkPageDP);
			
			//only perform mapping if we actually have discussions, i.e. have valid ContextTransactionData
			if(contextTransactionData.isAvailable()){
				//retrieve the discourse for the provided TalkPage
				Optional<Discourse> discourse = discourseService.findOne(curTalkPageDP);
				if(!discourse.isPresent()){
					logger.error("Could not retrieve the Discourse for the provided Talk Page DiscoursePart");
					continue;
				}
				

				// get primary keys for first and last revision in the window
				List<Timestamp> revTimestamps = revApi.getRevisionTimestampsBetweenTimestamps(articleId, new Timestamp(contextTransactionData.getFirstContent().getTime()), new Timestamp(contextTransactionData.getLastContent().getTime()));
				if(!revTimestamps.isEmpty()){
					int firstRevCounter = revApi.getRevision(articleId, revTimestamps.get(0)).getRevisionCounter();
					int lastRevPK = revApi.getRevision(articleId, revTimestamps.get(revTimestamps.size() - 1)).getPrimaryKey();

					// create revision iterator that iterates over the article revisions
					// between the two provided contribution timestamps
					RevisionIterator articleRevIt = new RevisionIterator(revApi.getRevisionApiConfiguration(), revApi.getFirstRevisionPK(articleId), lastRevPK, revApi.getConnection());
					articleRevIt.setShouldLoadRevisionText(false); //we need to skip ahead several revs, so don't load text by default

					//Process revisions and create content objects.
					//The content objects will represent a doubly linked list and they are eventually associated with the same context
					List<Long> ids = new ArrayList<>(); //keeps track of the (order of) revision ids 
					Revision previousArticleRev=null;
					Revision curArticleRev=null;
					boolean mappingStarted=false;
					while(articleRevIt.hasNext()){
						previousArticleRev = curArticleRev;
						curArticleRev = articleRevIt.next();
						if(curArticleRev.getRevisionCounter()<firstRevCounter){continue;}
						if(!mappingStarted){
							//we want to include one article revision before the time window of the discussion activity
							//this happens one per mapping cycle - i.e. once per article
							if(previousArticleRev!=null){
								ids.add(converterService.mapRevision(discourse.get().getId(),previousArticleRev,article.getTitle().getPlainTitle(),ids.size()>1?ids.get(ids.size()-1):null));
							}
							mappingStarted=true;
						}
						ids.add(converterService.mapRevision(discourse.get().getId(),curArticleRev,article.getTitle().getPlainTitle(),ids.size()>1?ids.get(ids.size()-1):null));
					}
					
					//update reference to first and last content element 
					//start and end time are already created
					if(!ids.isEmpty()){
						contribService.findOne(contextTransactionData.getContextId()).ifPresent(ctx->{
							contentService.findOne(ids.get(0)).ifPresent(firstContent->ctx.setFirstRevision(firstContent));
							contentService.findOne(ids.get(ids.size()-1)).ifPresent(currentContent->ctx.setCurrentRevision(currentContent));
							contribService.save(ctx);
							}
						);
					}

				}else{
					//in this case, there is no article revision activity during the time window of the discussion
					//we then retrieve the single revision that was current throughout the discussion
					Timestamp prevTs = null;
					for(Timestamp ts:revApi.getRevisionTimestamps(articleId)){
						if(prevTs!=null&&(ts.after(prevTs)||ts.equals(prevTs))){
							Revision lastestArticleRev = revApi.getRevision(articleId,prevTs);
							Long contentId = converterService.mapRevision(discourse.get().getId(),lastestArticleRev,article.getTitle().getPlainTitle(),null);
							
							contribService.findOne(contextTransactionData.getContextId()).ifPresent(ctx -> {
								contentService.findOne(contentId).ifPresent(content -> {
									ctx.setFirstRevision(content);
									ctx.setCurrentRevision(content);
								});
								contribService.save(ctx);
							});
							break;
						}
						prevTs=ts;
					}
				}				
			}			
		}

		logger.info("Finished mapping context articles.");

		// manually close the hibernate session for the Wikipedia connection
		// which is not managed by Spring
		WikiHibernateUtil.getSessionFactory(dbconf).close();
	}
}