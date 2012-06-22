package org.juxtasoftware.resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.importer.JuxtaXsltFactory;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

/**
 * REsource to get/update XSLT for a witness.
 * Also get the generic XSLT template.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class XsltResource extends BaseResource {
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private ComparisonSetDao setDao;
    private Long xsltId = null;
    private boolean templateRequest = false;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        Long witnessId = null;
        if ( getRequest().getAttributes().containsKey("id")) {
            witnessId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        }
        if ( getRequest().getAttributes().containsKey("xsltId")) {
            this.xsltId = Long.parseLong( (String)getRequest().getAttributes().get("xsltId"));
        }
        
        String lastSeg  = getRequest().getResourceRef().getLastSegment();
        this.templateRequest = ( lastSeg.equalsIgnoreCase("template"));
        
        validateParams(witnessId);
    }
    
    private void validateParams( final Long witnessId) {
        if ( this.templateRequest && (witnessId != null || this.xsltId != null) ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return;
        }
        
        if ( witnessId != null && this.xsltId != null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return;
        }
        
        if (witnessId != null ) {
            Witness w = this.witnessDao.find(witnessId);
            if ( validateModel(w) == false ) {
                return;
            }
            this.xsltId = w.getXsltId();
        }
    }
    
    @Get("json")
    public Representation getJson() {
        if ( this.templateRequest ) {
            return getXsltTemplates();
        }
        
        if ( this.xsltId != null ) {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(xslt));
        }
        
        // if all else has failed, just return the list of xslts
        List<JuxtaXslt> list = this.xsltDao.list(this.workspace);
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(list));
    }

    
    private Representation getXsltTemplates() {
        try {
            Map<String,String> templates = new HashMap<String,String>();
            templates.put("main", JuxtaXsltFactory.getGenericTemplate() );
            templates.put("singleExclude", JuxtaXsltFactory.getSingleExclusionTemplate() );
            templates.put("globalExclude", JuxtaXsltFactory.getGlobalExclusionTemplate() );
            templates.put("breaks", JuxtaXsltFactory.getBreaksTemplate() );
            templates.put("linebreak", "<xsl:value-of select=\"$display-linebreak\"/>");
            
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(templates));
        } catch (IOException e ) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to retrieve XSLT templates: " +e.getMessage());
        }
    }
    
    @Get("xml")
    public Representation getXml() {
        
        if ( this.xsltId != null ) {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            if ( xslt == null ) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND);
                return toTextRepresentation("xslt "+this.xsltId+" does not exist");
            }
            return toXmlRepresentation( xslt.getXslt() );
        }
        
        setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
        return null;
    }
    
    @Post("json")
    public Representation createXslt( final String jsonData ) {
        Gson gson = new Gson();
        JuxtaXslt xslt = gson.fromJson(jsonData, JuxtaXslt.class);
        Long id = this.xsltDao.create(xslt);
        return toTextRepresentation( id.toString() );
    }
    
    @Put
    public Representation updateXslt( final Representation entity ) {
        if ( this.templateRequest || this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        } 
        
        if (MediaType.TEXT_XML.equals(entity.getMediaType()) == false) {
            setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
            return null;
        }
        
        JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
        if ( validateModel(xslt) == false ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("xslt "+this.xsltId+" does not exist");
        }        
  
        try {
            this.xsltDao.update(this.xsltId, new InputStreamReader(entity.getStream()) );
            
            // Get the witness that uses this XSLT. List should be of size 1.
            List<Usage> usage = this.xsltDao.getUsage(xslt);
            for(Usage u : usage) {
                if ( u.getType().equals(Usage.Type.WITNESS)) {
                    Witness origWit = this.witnessDao.find( u.getId() );
                    Source src = this.sourceDao.find(this.workspace.getId(), origWit.getSourceId());
                    this.transformer.redoTransform(src, origWit);
                } else if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                    ComparisonSet set = this.setDao.find( u.getId());
                    this.setDao.clearCollationData(set);
                    
                }
            }
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(usage));
        } catch (Exception e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation(e.getMessage());
        }
    }
    
    @Delete
    public void deletXslt( ) {
        if ( this.xsltId != null ) {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            if ( validateModel(xslt) != false ) {
                try {
                    this.xsltDao.delete(xslt);
                } catch ( Exception e ) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "cannot to delete xslt that is in use");
                }
            } 
        }

        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
    }
}
