/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.deposit.assembler.dspace.mets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DspaceMetadataDomWriterFactory {

    private DocumentBuilderFactory dbf;

    @Autowired
    public DspaceMetadataDomWriterFactory(DocumentBuilderFactory dbf) {
        this.dbf = dbf;
    }

    public DspaceMetadataDomWriter newInstance() {
        return new DspaceMetadataDomWriter(dbf);
    }
}
