package edu.cmu.cs.lti.discoursedb.io.bazaar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

/**
 * POJO for the message information.
 * 
 * Boilerplate code will be autogenerated by Lombok at compile time. 
 * 
 * @author Haitian Gong
 * @author Oliver Ferschke
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "id", "roomid", "parentid", "created_time", "type", "content", "username", "useraddress" })
public class Message {
	
	private String id;
	private String roomid;
	private String created_time;
	private String type;
	private String content;
	private String username;
	private String parentid;
	private String useraddress;
	
}
