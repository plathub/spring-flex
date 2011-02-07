package org.springframework.flex.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import flex.messaging.MessageException;
import flex.messaging.io.MessageIOConstants;
import flex.messaging.io.SerializationContext;
import flex.messaging.io.SerializationException;
import flex.messaging.io.amf.ActionContext;
import flex.messaging.io.amf.ActionMessage;
import flex.messaging.io.amf.AmfMessageDeserializer;
import flex.messaging.io.amf.AmfMessageSerializer;
import flex.messaging.io.amf.AmfTrace;


public class AmfHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    private static final String AMF_ERROR = "Could not read input message body as AMF";
	private static final String ACTION_MSG_ERROR = "Could not read input message body as "+ActionMessage.class.getName();
	private static final Log log = LogFactory.getLog(AmfHttpMessageConverter.class);
    
    public AmfHttpMessageConverter() {
        super(MediaType.parseMediaType(MessageIOConstants.AMF_CONTENT_TYPE));
    }

	@Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    protected Object readInternal(Class<? extends Object> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        
        
        AmfTrace trace = null;
        if (log.isDebugEnabled()) {
            trace = new AmfTrace();
        }
        
        Object result = null;
        
        if (clazz.equals(ActionMessage.class)) {
        	result = readActionMessage(inputMessage, trace);
        } else {
        	result = readObject(inputMessage, trace);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Read AMF message:\n" + trace);
        }
        return result;
    }

    @Override
    protected void writeInternal(Object data, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
    	AmfTrace trace = null;
        if (log.isDebugEnabled()) {
            trace = new AmfTrace();
        }
        
        outputMessage.getHeaders().setPragma("no-cache");
        outputMessage.getHeaders().setCacheControl("no-cache, no-store, max-age=0");
        outputMessage.getHeaders().setExpires(1L);
        
        if (data instanceof ActionMessage) {
        	writeActionMessage((ActionMessage) data, outputMessage, trace);
        } else {
        	writeObject(data, outputMessage, trace);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Wrote AMF message:\n" + trace);
        }
    }
    
    private Object readObject(HttpInputMessage inputMessage, AmfTrace trace) throws IOException {
    	AmfMessageDeserializer deserializer = new AmfMessageDeserializer();
    	deserializer.initialize(new SerializationContext(), inputMessage.getBody(), trace);
    	
    	try {
            return deserializer.readObject();
        } catch (ClassNotFoundException cnfe) {
        	throw new HttpMessageNotReadableException(AMF_ERROR, cnfe);
        } catch (SerializationException se) {
        	throw new HttpMessageNotReadableException(AMF_ERROR, se);
        }
	}

	private ActionMessage readActionMessage(HttpInputMessage inputMessage, AmfTrace trace) throws IOException {
    	AmfMessageDeserializer deserializer = new AmfMessageDeserializer();
    	deserializer.initialize(new SerializationContext(), inputMessage.getBody(), trace);
    	
    	try {
        	ActionContext context = new ActionContext();
        	ActionMessage message = new ActionMessage();
        	context.setRequestMessage(message);
            deserializer.readMessage(message, context);
            return message;
        } catch (ClassNotFoundException cnfe) {
        	throw new HttpMessageNotReadableException(ACTION_MSG_ERROR, cnfe);
        } catch (MessageException me) {
        	throw new HttpMessageNotReadableException(ACTION_MSG_ERROR, me);
        }
    }
	
	private void writeActionMessage(ActionMessage message,
			HttpOutputMessage outputMessage, AmfTrace trace) throws IOException {
		AmfMessageSerializer serializer = new AmfMessageSerializer();
		ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
		serializer.setVersion(message.getVersion());
		serializer.initialize(new SerializationContext(), outBuffer, trace);
        
		try {
        	ActionContext context = new ActionContext();
        	context.setVersion(message.getVersion());
        	context.setResponseMessage(message);
        	serializer.writeMessage(message);
        	outBuffer.flush();
        	outBuffer.close();
        	outputMessage.getHeaders().setContentLength(outBuffer.size());
        	outBuffer.writeTo(outputMessage.getBody());
        } catch (SerializationException se) {
        	throw new HttpMessageNotWritableException("Could not write "+message+" as AMF message.", se);
        }
	}
    
    private void writeObject(Object data, HttpOutputMessage outputMessage,
			AmfTrace trace) throws IOException {
    	AmfMessageSerializer serializer = new AmfMessageSerializer();
    	serializer.setVersion(MessageIOConstants.AMF3);
    	ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    	serializer.initialize(new SerializationContext(), outBuffer, trace);
        
        try {
        	serializer.writeObject(data);
        	outBuffer.flush();
        	outBuffer.close();
        	outputMessage.getHeaders().setContentLength(outBuffer.size());
        	outBuffer.writeTo(outputMessage.getBody());
        } catch (SerializationException se) {
        	throw new HttpMessageNotWritableException("Could not write "+data+" as AMF message.", se);
        }
		
	}
}