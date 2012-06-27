package org.juxtasoftware.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

/**
 * Extract all namespace info from an XML source
 * @author loufoster
 *
 */
public final class NamespaceExtractor {
    
    public enum XmlType {GENERIC, TEI, RAM};
    
    /**
     * Scan the XML source and extract all of the namespace information
     * 
     * @param sourceReader
     * @return
     * @throws IOException
     */
    public static Set<NamespaceInfo> extract( final Reader sourceReader ) throws IOException {
        BufferedReader br = new BufferedReader( sourceReader );
        Set<NamespaceInfo> namespaces = new HashSet<NamespaceInfo>();
        final String defaultNs = "xmlns=\"";
        final String noNamespace = ":noNamespaceSchemaLocation=\"";
        final String ns = "xmlns:";
        while (true) {
            String line = br.readLine();
            if ( line == null ) {
                break;
            } else {
                // default namespace?
                if ( line.contains(defaultNs) ) {
                    int pos = line.indexOf(defaultNs)+defaultNs.length();
                    int end = line.indexOf('"', pos);
                    NamespaceInfo info = new NamespaceInfo(null, line.substring(pos,end), false);
                    namespaces.add( info );
                } 
                
                // no-namespace loc?
                if ( line.contains(noNamespace) ) {
                    int pos = line.indexOf(noNamespace)+noNamespace.length();
                    int end = line.indexOf('"', pos);
                    NamespaceInfo info = new NamespaceInfo(null, line.substring(pos,end), true);
                    namespaces.add( info );             
                } 
                
                // specifc namespace(s)?
                if ( line.contains(ns) ) {                
                    int pos = line.indexOf(ns)+ns.length();
                    while ( pos > -1  ) {
                        int nsPos = pos;
                        int nsEndPos = line.indexOf("=\"", pos);
                        pos = nsEndPos+2;
                        int end = line.indexOf('"', pos);
                        String url = line.substring(pos,end);
                        if ( url.contains("XMLSchema-instance") == false ) {
                            String prefix = line.substring(nsPos,nsEndPos);
                            namespaces.add( new NamespaceInfo(prefix, url, false) );
                        }
                        int newPos = line.indexOf(ns, end);
                        if (newPos > -1 ) {
                            pos = newPos+6;
                        } else {
                            pos = -1;
                        }
                    }
                }
            }
        }
        
        return namespaces;
    }
    
    /**
     * Examine the namespace declarations of this source and attempt to determine
     * the XML type: TEI, RAM or Generic
     * 
     * @param srcReader
     * @return
     */
    public static XmlType determineXmlType(final Reader srcReader) {
        BufferedReader  br = new BufferedReader(srcReader);
        boolean foundNs = false;
        int noNsCount = 0;
        XmlType type = XmlType.GENERIC;
        try {
            while ( true ) {
                String line = br.readLine();
                if ( line == null ) {
                    break;
                } else {
                    if ( foundNs == false ) {
                        foundNs = line.contains(" xmlns") ;
                    } 
                    
                    if ( foundNs == true) {
                        if ( line.contains(" xmlns")==false ) {
                            noNsCount++;
                            // once the first namespace has been found,
                            // give up if we go a bit and see no more
                            if (noNsCount > 5 ) {
                                break;
                            }
                        } else {
                            if ( line.contains("http://www.tei-")) {
                                type = XmlType.TEI;
                                break;
                            } else if ( line.contains("ram.xsd")) {
                                type = XmlType.RAM;
                                break;
                            }
                        }
                    }
                } 
            }
        } catch (IOException e ) {
            // swallow it
        } finally {
            IOUtils.closeQuietly(br);
        }
        return type;
    }
    
    public static class NamespaceInfo {
        private final String prefix;
        private final String url;
        private String defaultPrefix;
        private final boolean noNamespace;
        
        /**
         * Create a blank namespace
         */
        public NamespaceInfo( ) {
            this("","",true);
        }
        
        /**
         * Create a namespace
         * @param prefix Namespae prefix. This may be null when a default namespace is in use
         * @param url Schema URL
         * @param noNs Set this flag when noNamespaceSchemaLocation is present
         */
        public NamespaceInfo( final String prefix, final String url, final boolean noNs ) {
            this.prefix = prefix;
            this.url = url;
            this.defaultPrefix = "jxt";
            this.noNamespace = noNs;
        }
        
        public String getDefaultPrefix() {
            return this.defaultPrefix;
        }
        public void setDefaultPrefix(String string) {
            this.defaultPrefix = string;
        }
        public boolean hasNoNamespace() {
            return this.noNamespace;
        }
        
        public boolean isDefault() {
            return (this.prefix == null);
        }
        public String getPrefix() {
            if ( hasNoNamespace() ) {
                return "";
            }
            
            if ( this.prefix == null ) {
                return getDefaultPrefix();
            }
            
            return this.prefix;
        }
        
        public String getUrl() {
            return this.url;
        }
        
        public String toString() {
            String p = getPrefix();
            if ( p.length() > 0) {
                return "xmlns:"+getPrefix()+"=\""+getUrl()+"\"";
            }
            return "xmlns=\""+getUrl()+"\"";
        }
        
        
        public String addNamespacePrefix( final String tag ) {
            if ( hasNoNamespace() == false  ) {
                return getPrefix() +":" + tag;
            } 
            return tag;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
            result = prime * result + ((url == null) ? 0 : url.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            NamespaceInfo other = (NamespaceInfo) obj;
            if (prefix == null) {
                if (other.prefix != null)
                    return false;
            } else if (!prefix.equals(other.prefix))
                return false;
            if (url == null) {
                if (other.url != null)
                    return false;
            } else if (!url.equals(other.url))
                return false;
            return true;
        }        
    }
}
