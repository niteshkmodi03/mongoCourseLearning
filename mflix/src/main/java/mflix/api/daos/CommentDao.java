package mflix.api.daos;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import mflix.api.models.Comment;
import mflix.api.models.Critic;

@Component
public class CommentDao extends AbstractMFlixDao {

    public static String COMMENT_COLLECTION = "comments";
    private final Logger log;
    private MongoCollection<Comment> commentCollection;
    private CodecRegistry pojoCodecRegistry;

    @Autowired
    public CommentDao(
            MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        log = LoggerFactory.getLogger(this.getClass());
        this.db = this.mongoClient.getDatabase(MFLIX_DATABASE);
        this.pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        this.commentCollection =
                db.getCollection(COMMENT_COLLECTION,Comment.class).withCodecRegistry(pojoCodecRegistry);
    }

    /**
     * Returns a Comment object that matches the provided id string.
     *
     * @param id - comment identifier
     * @return Comment object corresponding to the identifier value
     */
    public Comment getComment(String id) {
    	if(id!=null || id!="") {
    		 Comment comment = commentCollection.find(new Document("_id", new ObjectId(id))).first();
    		 if(comment!=null) {
    			 return comment;
    		 }
    	}
//        Comment comment = new Comment();
//        comment.setOid(document.getObjectId("_id"));
//        comment.setEmail(document.getString("email"));
//        comment.setText(document.getString("text"));
        return null;
    }

    /**
     * Adds a new Comment to the collection. The equivalent instruction in the mongo shell would be:
     *
     * <p>db.comments.insertOne({comment})
     *
     * <p>
     *
     * @param comment - Comment object.
     * @throw IncorrectDaoOperation if the insert fails, otherwise
     * returns the resulting Comment object.
     */
    public Comment addComment(Comment comment) {
    	if(comment.getId()==null || comment.getId().isEmpty()) {
    		throw new IncorrectDaoOperation("Comment Id can not be null");
    	}
    	try {
    	commentCollection.insertOne(comment);
    	return comment;
    	}catch(MongoWriteException e) {
    		String errorMessage =
    		        MessageFormat.format(
    		            "Error occurred while adding a new Comment `{}`: {}", comment, e.getMessage());
    		    throw new IncorrectDaoOperation(errorMessage);
    	}
//        // TODO> Ticket - Update User reviews: implement the functionality that enables adding a new
//        // comment.
//        // TODO> Ticket - Handling Errors: Implement a try catch block to
//        // handle a potential write exception when given a wrong commentId.
    }

    /**
     * Updates the comment text matching commentId and user email. This method would be equivalent to
     * running the following mongo shell command:
     *
     * <p>db.comments.update({_id: commentId}, {$set: { "text": text, date: ISODate() }})
     *
     * <p>
     *
     * @param commentId - comment id string value.
     * @param text      - comment text to be updated.
     * @param email     - user email.
     * @return true if successfully updates the comment text.
     */
    public boolean updateComment(String commentId, String text, String email) {
        
    	Bson filter = Filters.and(
                Filters.eq("email", email),
                Filters.eq("_id", new ObjectId(commentId)));
        Bson update = Updates.combine(Updates.set("text", text),
                                      Updates.set("date", new Date()));
        try {
        UpdateResult res = commentCollection.updateOne(filter, update);

        if(res.getMatchedCount() > 0){

            if (res.getModifiedCount() != 1){
                log.warn("Comment `{}` text was not updated. Is it the same text?");
            }

            return true;
        }
        log.error("Could not update comment `{}`. Make sure the comment is owned by `{}`",
                   commentId, email);
        return false;
        }catch(MongoWriteException e) {
        	String messageError =
        	        MessageFormat.format(
        	            "Error occurred while updating comment `{}`: {}", commentId, e.getMessage());
        	    throw new IncorrectDaoOperation(messageError);
        }
        // TODO> Ticket - Update User reviews: implement the functionality that enables updating an
        // user own comments
        // TODO> Ticket - Handling Errors: Implement a try catch block to
        // handle a potential write exception when given a wrong commentId.
    }

    /**
     * Deletes comment that matches user email and commentId.
     *
     * @param commentId - commentId string value.
     * @param email     - user email value.
     * @return true if successful deletes the comment.
     */
    public boolean deleteComment(String commentId, String email) {
    	
    
    	try {
    	Bson filter = Filters.and(Filters.eq("_id", new ObjectId(commentId)),Filters.eq("email", email));
    	DeleteResult deleteResult = commentCollection.deleteOne(filter);
    	if(deleteResult.getDeletedCount()==1)
    	  return true;
    	else return false;
    	}catch(MongoWriteException  e) {
    		String errorMessage =
    		        MessageFormat.format("Error deleting comment " + "`{}`: {}", commentId, e);
    		    throw new IncorrectDaoOperation(errorMessage);
    	}
        // TODO> Ticket Delete Comments - Implement the method that enables the deletion of a user
        // comment
        // TIP: make sure to match only users that own the given commentId
        // TODO> Ticket Handling Errors - Implement a try catch block to
        // handle a potential write exception when given a wrong commentId.
    }

    /**
     * Ticket: User Report - produce a list of users that comment the most in the website. Query the
     * `comments` collection and group the users by number of comments. The list is limited to up most
     * 20 commenter.
     *
     * @return List {@link Critic} objects.
     */
    public List<Critic> mostActiveCommenters() {
        List<Critic> mostActive = new ArrayList<>();
        // // TODO> Ticket: User Report - execute a command that returns the
        // // list of 20 users, group by number of comments. Don't forget,
        // // this report is expected to be produced with an high durability
        // // guarantee for the returned documents. Once a commenter is in the
        // // top 20 of users, they become a Critic, so mostActive is composed of
        // // Critic objects.
        
        List<Bson> pipeline = new LinkedList<>();
		//BsonField field = new BsonField("",	);
        Bson unwindEmailStage = Aggregates.unwind("$email");
        String groupIdEmail = "$email";
        BsonField sum1 = Accumulators.sum("count", 1);

        // adding both group _id and accumulators
        Bson groupStage = Aggregates.group(groupIdEmail, sum1);
        
		Bson sort = Aggregates.sort(Sorts.descending("count"));
		Bson limit =Aggregates.limit(20);
		
		pipeline.add(unwindEmailStage);
		pipeline.add(groupStage);
		pipeline.add(sort);
		pipeline.add(limit);
		
//		AggregateIterable<Document> iterable =commentCollection.aggregate(pipeline);
//        
//		System.out.println(iterable);
//		
//		for(Document comment:iterable) {
//			Critic critic = new Critic(comment.getString("_id"), comment.getInteger("count"));
//			mostActive.add(critic);
//		}
        return mostActive;
        
        
    }
    
    public List<Critic> mostActiveCommenters2(){
        List<Critic> mostActive = new ArrayList<>();

        /**
         * In this method we can use the $sortByCount stage:
         * https://docs.mongodb.com/manual/reference/operator/aggregation/sortByCount/index.html
         * using the $email field expression.
         */
        Bson groupByCountStage = Aggregates.sortByCount("$email");
        // Let's sort descending on the `count` of comments
        Bson sortStage = Aggregates.sort(Sorts.descending("count"));
        // Given that we are required the 20 top users we have to also $limit
        // the resulting list
        Bson limitStage = Aggregates.limit(20);
        // Add the stages to a pipeline
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(groupByCountStage);
        pipeline.add(sortStage);
        pipeline.add(limitStage);

        // We cannot use the CommentDao class `commentCollection` object
        // since this returns Comment objects.
        // We need to create a new collection instance that returns
        // Critic objects instead.
        // Given that this report is required to be accurate and
        // reliable, we want to guarantee a high level of durability, by
        // ensuring that the majority of nodes in our Replica Set
        // acknowledged all documents for this query. Therefore we will be
        // setting our ReadConcern to "majority"
        // https://docs.mongodb.com/manual/reference/method/cursor.readConcern/
        MongoCollection<Critic> commentCriticCollection =
                this.db.getCollection("comments", Critic.class)
                        .withCodecRegistry(this.pojoCodecRegistry)
                        .withReadConcern(ReadConcern.MAJORITY);

        // And execute the aggregation command output in our collection object.
        commentCriticCollection.aggregate(pipeline).into(mostActive);
        return mostActive;
    }
}
