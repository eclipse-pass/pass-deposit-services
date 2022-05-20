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

package org.dataconservancy.pass.deposit.builder.fs;

import static org.dataconservancy.pass.deposit.model.JournalPublicationType.parseTypeDescription;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositManifest;
import org.dataconservancy.pass.deposit.model.DepositMetadata;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.File;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * Base class for copying deposit-submission data from Fedora-based sources into the deposit data model.
 * Copies relevant fields from a collection of PassEntity objects, starting with the Submission entity
 * that is the root of the data tree.
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
abstract class ModelBuilder {

    static final Logger LOG = LoggerFactory.getLogger(ModelBuilder.class);

    private static final String ISSNS = "issns";

    private static final String ISSN = "issn";

    private static final String MANUSCRIPT_TITLE_KEY = "title";

    private static final String ABSTRACT_KEY = "abstract";

    private static final String JOURNAL_TITLE_KEY = "journal-title";

    private static final String VOLUME_KEY = "volume";

    private static final String ISSUE_KEY = "issue";

    private static final String DOI_KEY = "doi";

    private static final String PUBLISHER_KEY = "publisher";

    private static final String PUBLICATION_DATE_KEY = "publicationDate";

    private static final String EMBARGO_END_DATE_KEY = "Embargo-end-date";

    private static final String AUTHORS_KEY = "authors";

    private static final String AUTHOR_KEY = "author";

    private static final String PUB_TYPE_KEY = "pubType";

    private static final String EMBARGO_END_DATE_PATTERN = "yyyy-MM-dd";

    private static final String NLMTA_KEY = "journal-NLMTA-ID";

    private static final String METADATA_BLOB_KEY = "metadata";

    /**
     * Creates a DepositMetadata person with the person's context passed as parameters.
     *
     * @param userEntity
     * @param type
     * @return
     */
    private DepositMetadata.Person createPerson(User userEntity, DepositMetadata.PERSON_TYPE type) {
        DepositMetadata.Person person = new DepositMetadata.Person();
        person.setFirstName(userEntity.getFirstName());
        person.setMiddleName(userEntity.getMiddleName());
        person.setLastName(userEntity.getLastName());
        person.setFullName(userEntity.getDisplayName());
        person.setEmail(userEntity.getEmail());
        person.setType(type);

        return person;
    }

    /**
     * Creates a Person representing an author with the given name.
     *
     * @param fullName
     * @return
     */
    private DepositMetadata.Person createAuthor(String fullName) {
        DepositMetadata.Person person = new DepositMetadata.Person();
        person.setFullName(fullName);
        person.setType(DepositMetadata.PERSON_TYPE.author);
        return person;
    }

    /**
     * Convenience method for retrieving a boolean property.
     *
     * @param parent
     * @param name
     * @return
     */
    private Optional<Boolean> getBooleanProperty(JsonObject parent, String name) {
        if (parent.has(name)) {
            return Optional.of(parent.get(name).getAsBoolean());
        }
        return Optional.empty();
    }

    /**
     * Convenience method for retrieving a string property.
     *
     * @param parent
     * @param name
     * @return
     */
    private Optional<String> getStringProperty(JsonObject parent, String name) {
        if (parent.has(name) && !parent.get(name).isJsonNull()) {
            return Optional.of(parent.get(name).getAsString());
        }

        return Optional.empty();
    }

    /**
     * Convenience method for retrieving an object property.
     *
     * @param parent
     * @param name
     * @return
     */
    private Optional<JsonObject> getObjectProperty(JsonObject parent, String name) {
        if (parent.has(name) && !parent.get(name).isJsonNull() && parent.get(name).isJsonObject()) {
            return Optional.of(parent.get(name).getAsJsonObject());
        }

        return Optional.empty();
    }

    /**
     * Convenience method for retrieving an array property.
     *
     * @param parent
     * @param name
     * @return
     */
    private Optional<JsonArray> getArrayProperty(JsonObject parent, String name) {
        if (parent.has(name) && !parent.get(name).isJsonNull() && parent.get(name).isJsonArray()) {
            return Optional.of(parent.get(name).getAsJsonArray());
        }

        return Optional.empty();
    }

    private void processCommonMetadata(DepositMetadata metadata, JsonObject submissionData)
        throws InvalidModel {

        // Is this tile for manuscript or article or both?
        getStringProperty(submissionData, MANUSCRIPT_TITLE_KEY)
            .ifPresent(title -> {
                metadata.getManuscriptMetadata().setTitle(title);
                metadata.getArticleMetadata().setTitle(title);
            });

        getStringProperty(submissionData, ABSTRACT_KEY)
            .ifPresent(abs -> metadata.getManuscriptMetadata().setMsAbstract(abs));

        getStringProperty(submissionData, JOURNAL_TITLE_KEY)
            .ifPresent(jTitle -> metadata.getJournalMetadata().setJournalTitle(jTitle));

        getStringProperty(submissionData, VOLUME_KEY)
            .ifPresent(volume -> metadata.getArticleMetadata().setVolume(volume));

        getStringProperty(submissionData, ISSUE_KEY)
            .ifPresent(issue -> metadata.getArticleMetadata().setIssue(issue));

        getArrayProperty(submissionData, AUTHORS_KEY).ifPresent(authors -> {
            authors.forEach(authorElement -> {
                getStringProperty(authorElement.getAsJsonObject(), AUTHOR_KEY)
                    .ifPresent(name -> metadata.getPersons().add(createAuthor(name)));
            });
        });

        getStringProperty(submissionData, PUBLISHER_KEY)
            .ifPresent(pName -> metadata.getJournalMetadata().setPublisherName(pName));

        getStringProperty(submissionData, PUBLICATION_DATE_KEY)
            .ifPresent(pName -> metadata.getJournalMetadata().setPublicationDate(pName));

        getArrayProperty(submissionData, ISSNS).ifPresent(issns -> {
            issns.forEach(issnObjAsStr -> {
                try {
                    JsonObject issnObj = issnObjAsStr.getAsJsonObject();

                    Optional<String> issn = getStringProperty(issnObj, ISSN);
                    Optional<String> pubType = getStringProperty(issnObj, PUB_TYPE_KEY);

                    issn.ifPresent(i -> pubType.ifPresent(p -> metadata.getJournalMetadata()
                        .getIssnPubTypes().putIfAbsent(i,
                            new DepositMetadata.IssnPubType(i, parseTypeDescription(p)))));
                } catch (Exception e) {
                    // Shouldn't happen.  If ISSNs can't be parsed, then they should be ignored, and not included
                    // in the Journal metadata
                    LOG.warn("Unable to parse ISSNs from '{}'", issnObjAsStr);
                }
            });
        });

        getStringProperty(submissionData, EMBARGO_END_DATE_KEY).ifPresent(endDate -> {
            try {
                // TODO - Resolve inconsistent date/date-time formats in metadata and deposit data model
                // TODO - Fix assumption of local timezone
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(EMBARGO_END_DATE_PATTERN);
                LocalDateTime localEndDate = LocalDate.parse(endDate, formatter).atStartOfDay();
                ZonedDateTime zonedEndDate = localEndDate.atZone(ZoneId.of("America/New_York"));
                metadata.getArticleMetadata().setEmbargoLiftDate(zonedEndDate);
            } catch (Exception e) {
                InvalidModel im = new InvalidModel(String.format("Data file contained an invalid Date: '%s'.",
                                                                 endDate), e);
                throw new RuntimeException(im.getMessage(), im);
            }
        });
    }

    private void processCrossrefMetadata(DepositMetadata metadata, JsonObject submissionData) {
        getStringProperty(submissionData, DOI_KEY).ifPresent(doi -> {
            try {
                doi = doi.trim();
                metadata.getArticleMetadata().setDoi(URI.create(doi));
            } catch (Exception e) {
                InvalidModel im = new InvalidModel(String.format("Data file contained an invalid DOI: '%s'", doi), e);
                throw new RuntimeException(im.getMessage(), im);
            }
        });
    }

    private void processPmcMetadata(DepositMetadata metadata, JsonObject submissionData) {
        getStringProperty(submissionData, NLMTA_KEY).ifPresent(nlmta ->
                                                                   metadata.getJournalMetadata().setJournalId(nlmta));
    }

    /**
     * Processes the so-called "blob" metadata, reverse engineered from the sample blob here:
     * https://github.com/OA-PASS/nihms-submission/issues/122#issuecomment-399314521
     *
     * @param depositMetadata
     * @param metadataStr
     * @throws InvalidModel
     */
    void processMetadata(DepositMetadata depositMetadata, String metadataStr)
        throws InvalidModel {
        JsonObject json = new JsonParser().parse(metadataStr).getAsJsonObject();
        processCommonMetadata(depositMetadata, json);
        processPmcMetadata(depositMetadata, json);
        processCrossrefMetadata(depositMetadata, json);
    }

    /**
     * Creates a DepositSubmission by walking the tree of PassEntity objects, starting with the Submission entity,
     * copying the desired source data into a new DepositSubmission data model.
     *
     * @param submissionEntity
     * @param entities
     * @return
     * @throws InvalidModel
     */
    DepositSubmission createDepositSubmission(Submission submissionEntity, HashMap<URI, PassEntity> entities)
        throws InvalidModel {

        // The submission object to populate
        DepositSubmission submission = new DepositSubmission();

        // Prepare for Metadata
        DepositMetadata metadata = new DepositMetadata();
        submission.setMetadata(metadata);
        submission.setSubmissionMeta(new JsonParser().parse(submissionEntity.getMetadata()).getAsJsonObject());
        DepositMetadata.Manuscript manuscript = new DepositMetadata.Manuscript();
        metadata.setManuscriptMetadata(manuscript);
        DepositMetadata.Article article = new DepositMetadata.Article();
        metadata.setArticleMetadata(article);
        DepositMetadata.Journal journal = new DepositMetadata.Journal();
        metadata.setJournalMetadata(journal);
        ArrayList<DepositMetadata.Person> persons = new ArrayList<>();
        metadata.setPersons(persons);

        // Data from the Submission resource
        submission.setId(submissionEntity.getId().toString());
        // The deposit data model requires a "name" - for now we use the ID.
        submission.setName(submissionEntity.getId().toString());

        submission.setSubmissionDate(submissionEntity.getSubmittedDate());

        // Data from the Submission's user resource

        if (submissionEntity.getSubmitter() == null) {
            throw new InvalidModel("Submitter is undefined for submission " + submissionEntity.getId());
        }

        User userEntity = (User) entities.get(submissionEntity.getSubmitter());

        if (userEntity == null) {
            throw new InvalidModel("Could not find User entity for " + submissionEntity.getSubmitter());
        }

        persons.add(createPerson(userEntity, DepositMetadata.PERSON_TYPE.submitter));

        // As of 5/14/18, the following data is available from both the Submission metadata
        // and as a member of one of the PassEntity objects referenced by the Submission:
        //      manuscript: title, abstract, volume, issue
        //      journal: title, issn, NLMTA-ID
        // The metadata values are processed AFTER the PassEntity objects so they have precedence.
        processMetadata(metadata, submissionEntity.getMetadata());

        // Data from the Grant resources
        for (URI grantUri : submissionEntity.getGrants()) {
            Grant grantEntity = (Grant) entities.get(grantUri);

            // Data from the User resources for the PI and CoPIs
            User piEntity = (User) entities.get(grantEntity.getPi());
            persons.add(createPerson(piEntity, DepositMetadata.PERSON_TYPE.pi));
            for (URI copiUri : grantEntity.getCoPis()) {
                User copiEntity = (User) entities.get(copiUri);
                persons.add(createPerson(copiEntity, DepositMetadata.PERSON_TYPE.copi));
            }
        }

        // Add Manifest and Files
        DepositManifest manifest = new DepositManifest();
        submission.setManifest(manifest);
        ArrayList<DepositFile> files = new ArrayList<>();
        submission.setFiles(files);
        manifest.setFiles(files);

        for (URI key : entities.keySet()) {
            PassEntity entity = entities.get(key);
            if (entity instanceof File) {
                File file = (File) entity;
                // Ignore any Files that do not reference this Submission
                if (file.getSubmission().toString().equals(submissionEntity.getId().toString())) {
                    DepositFile depositFile = new DepositFile();
                    depositFile.setName(file.getName());
                    depositFile.setLocation(file.getUri().toString());
                    // TODO - The client model currently only has "manuscript" and "supplement" roles.
                    depositFile.setType(getTypeForRole(file.getFileRole()));
                    depositFile.setLabel(file.getDescription());
                    files.add(depositFile);
                }
            }
        }

        return submission;
    }

    private DepositFileType getTypeForRole(File.FileRole role) {
        if (role.equals(File.FileRole.SUPPLEMENTAL)) {
            return DepositFileType.supplement;
        } else {
            return DepositFileType.valueOf(role.name().toLowerCase());
        }
    }
}
