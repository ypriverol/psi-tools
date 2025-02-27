package psidev.psi.tools.validator;

import psidev.psi.tools.validator.rules.Rule;
import psidev.psi.tools.validator.schema.SaxMessage;

import java.util.Objects;

/**
 * The definition of a Validator message.
 *
 * @author Samuel Kerrien (skerrien@ebi.ac.uk)
 * @version $Id: ValidatorMessage.java 656 2007-06-29 11:18:19 +0100 (Fri, 29 Jun 2007) skerrien $
 * @since <pre>26-Dec-2005</pre>
 */
public class ValidatorMessage<T extends Object> {

    private static final String NEW_LINE = System.getProperty( "line.separator" );

    ///////////////////////////////
    // Instance variables

    /**
     * the message.
     */
    private final String message;

    /**
     * Level of the message. it qualifies the severity of the error.
     */
    private final MessageLevel level;

    /**
     * Context supposed to help the user to figure out where is the error coming from.
     */
    private Context context;

    /**
     * The Rule that generated that message.
     */
    private Rule rule;

    /////////////////////////////
    // Constructor

    public ValidatorMessage( String message, MessageLevel level ) {

        if ( message == null ) {
            throw new IllegalArgumentException( "A message must not be null when creating a ValidatorMessage." );
        }
        this.message = message;

        if ( level == null ) {
            throw new IllegalArgumentException( "A message level must not be null when creating a ValidatorMessage." );
        }
        this.level = level;
    }

    public ValidatorMessage( String message, MessageLevel level, Context context, Rule rule ) {

        this(message, level );

        if ( context == null ) {
            throw new IllegalArgumentException( "A context must not be null when creating a ValidatorMessage." );
        }
        this.context = context;

        if ( rule == null ) {
            throw new IllegalArgumentException( "A rule must not be null when creating a ValidatorMessage." );
        }
        this.rule = rule;
    }

    public ValidatorMessage( SaxMessage saxMessage, MessageLevel level ) {

        if ( level == null ) {
            throw new IllegalArgumentException( "A message level must not be null when creating a ValidatorMessage." );
        }
        this.level = level;

        if( saxMessage == null ) {
            throw new IllegalArgumentException( "SAX Message must not be null" );
        }

        StringBuilder sb = new StringBuilder( 100 );

        sb.append( "L" ).append( saxMessage.getLineNumber() );
        sb.append( " C" ).append( saxMessage.getColumnNumber() );
        sb.append( ":" ).append( saxMessage.getMessage() );

        message = sb.toString();
    }


    //////////////////////////////
    // Getters

    public String getMessage() {
        return message;
    }

    public MessageLevel getLevel() {
        return level;
    }

    public Context getContext() {
        return context;
    }

    public Rule getRule() {
        return rule;
    }

    ///////////////////////////
    // Object's override.

    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }

        final ValidatorMessage that = (ValidatorMessage) o;

        if (!Objects.equals(context, that.context)) {
            return false;
        }
        if ( level != that.level ) {
            return false;
        }
        if ( !message.equals( that.message ) ) {
            return false;
        }
        if (!Objects.equals(rule, that.rule)) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result;
        result = message.hashCode();
        result = 29 * result + level.hashCode();
        result = 29 * result + ( context != null ? context.hashCode() : 0 );
        result = 29 * result + ( rule != null ? rule.hashCode() : 0 );
        return result;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append( "ValidatorMessage" );
        sb.append( "{message='" ).append( message ).append( '\'' );
        sb.append( ", level=" ).append( level );
        sb.append( ", context=" ).append( context );
        sb.append( ", rule=" ).append( rule );
        sb.append( '}' );
        return sb.toString();
    }
}