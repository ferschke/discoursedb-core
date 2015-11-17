package edu.cmu.cs.lti.discoursedb.io.wikipedia.talk.converter;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.cmu.cs.lti.discoursedb.core.model.macro.Content;
import edu.cmu.cs.lti.discoursedb.core.model.macro.Contribution;
import edu.cmu.cs.lti.discoursedb.core.model.macro.Discourse;
import edu.cmu.cs.lti.discoursedb.core.model.macro.DiscoursePart;
import edu.cmu.cs.lti.discoursedb.core.model.system.DataSourceInstance;
import edu.cmu.cs.lti.discoursedb.core.model.user.User;
import edu.cmu.cs.lti.discoursedb.core.service.macro.ContentService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.ContributionService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.DiscoursePartService;
import edu.cmu.cs.lti.discoursedb.core.service.macro.DiscourseService;
import edu.cmu.cs.lti.discoursedb.core.service.system.DataSourceService;
import edu.cmu.cs.lti.discoursedb.core.service.user.UserService;
import edu.cmu.cs.lti.discoursedb.core.type.ContributionTypes;
import edu.cmu.cs.lti.discoursedb.core.type.DiscoursePartRelationTypes;
import edu.cmu.cs.lti.discoursedb.core.type.DiscoursePartTypes;
import edu.cmu.cs.lti.discoursedb.io.wikipedia.talk.model.TalkPage;
import edu.cmu.cs.lti.discoursedb.io.wikipedia.talk.model.Topic;
import edu.cmu.cs.lti.discoursedb.io.wikipedia.talk.model.Turn;
import edu.cmu.cs.lti.discoursedb.io.wikipedia.talk.model.WikipediaSourceMapping;

@Transactional(propagation= Propagation.REQUIRED, readOnly=false)
@Service
public class WikipediaTalkPageConverterService{

	private static final Logger logger = LogManager.getLogger(WikipediaTalkPageConverterService.class);
	
	@Autowired private DiscourseService discourseService;
	@Autowired private DataSourceService dataSourceService;
	@Autowired private DiscoursePartService discoursePartService;
	@Autowired private ContributionService contributionService;
	@Autowired private ContentService contentService;
	@Autowired private UserService userService;
	
	public void mapTalkPage(String discourseName, String dataSetName, String articleTitle, TalkPage tp){
		if(discourseName==null||discourseName.isEmpty()||dataSetName==null||dataSetName.isEmpty()||tp==null||articleTitle==null||articleTitle.isEmpty()){
			logger.error("Cannot map talk page. Data provided is complete. Skipping ...");
			return;
		}
		
		Discourse discourse = discourseService.createOrGetDiscourse(discourseName);
		//we create ONE discourse part for ALL the discussion activity in the context of a single article.
		//That is, the main talk page and all archives are aggregated in this one DiscoursePart
		DiscoursePart curArticleDP = discoursePartService.createOrGetTypedDiscoursePart(discourse, articleTitle, DiscoursePartTypes.TALK_PAGE);
		
		List<Topic> topics = tp.getTopics();
		logger.debug("Mapping "+topics.size()+" threads.");
		for(Topic topic:topics){
			logger.trace("Mapping topic "+topic.getTitle());
			
			DiscoursePart curTopicDP = discoursePartService.createOrGetTypedDiscoursePart(discourse, topic.getTitle(), DiscoursePartTypes.THREAD);			
			dataSourceService.addSource(curTopicDP, new DataSourceInstance(tp.getTpBaseRevision().getRevisionID()+"_"+topic.getTitle(), WikipediaSourceMapping.DISCUSSION_TITLE_ON_TALK_PAGE_TO_DISCOURSEPART, dataSetName));
			discoursePartService.createDiscoursePartRelation(curArticleDP, curTopicDP, DiscoursePartRelationTypes.TALK_PAGE_HAS_DISCUSSION);			

			List<Turn> turns = topic.getUserTurns();
			logger.debug("Mapping "+turns.size()+" turns.");
			for(Turn turn:turns){				
				logger.trace("Mapping turn "+turn.getTurnNr());
				
				//user information only defined by user name and very likely to re-occur on other discussion pages
				//so there is no point in adding a data source for users here
				User curAuthor = userService.createOrGetUser(discourse, turn.getContributor());				
				
				Content turnContent = contentService.createContent();
				turnContent.setText(turn.getText());
				turnContent.setStartTime(turn.getTimestamp());
				turnContent.setAuthor(curAuthor);
				//data source of contribution is a combination of topic title and turn number. 
				//the revision of the talk page is defined in the data source of the discourse part that wraps all turns of a discussion 
				dataSourceService.addSource(turnContent, new DataSourceInstance(topic.getTitle()+"_"+turn.getTurnNr(), WikipediaSourceMapping.TURN_NUMBER_IN_DISCUSSION_TO_CONTENT, dataSetName));					
				

				//the first contribution of a discussion should be a THREAD_STARTER, all others a POST
				Contribution turnContrib = turn.getTurnNr() == 1
						? contributionService.createTypedContribution(ContributionTypes.THREAD_STARTER)
						: contributionService.createTypedContribution(ContributionTypes.POST);
				turnContrib.setStartTime(turn.getTimestamp());
				discoursePartService.addContributionToDiscoursePart(turnContrib, curTopicDP);
				turnContrib.setCurrentRevision(turnContent);
				turnContrib.setFirstRevision(turnContent);
				//data source of contribution is a combination of topic title and turn number. 
				//the revision of the talk page is defined in the data source of the discourse part that wraps all turns of a discussion 
				dataSourceService.addSource(turnContrib, new DataSourceInstance(topic.getTitle()+"_"+turn.getTurnNr(), WikipediaSourceMapping.TURN_NUMBER_IN_DISCUSSION_TO_CONTRIBUTION, dataSetName));
			}
		}
	}
}