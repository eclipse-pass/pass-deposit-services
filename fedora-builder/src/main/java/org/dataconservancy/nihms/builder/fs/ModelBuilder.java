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

package org.dataconservancy.nihms.builder.fs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.dataconservancy.nihms.builder.InvalidModel;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositManifest;
import org.dataconservancy.nihms.model.DepositMetadata;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.File;
import org.dataconservancy.pass.model.Funder;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Journal;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.User;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

/***
 * Base class for copying deposit-submission data from Fedora-based sources into the deposit data model.
 * Copies relevant fields from a collection of PassEntity objects, starting with the Submission entity
 * that is the root of the data tree.
 *
 * Presently, neither the Fedora data model nor the deposit submission data model are finalized,
 * so many fields from the models are still unused or unset.  These fields are identified in
 * comments in this class.
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
abstract class ModelBuilder {

    // Creates a DepositMetadata person with the person's context passed as parameters.
    private DepositMetadata.Person createPerson(User userEntity, DepositMetadata.PERSON_TYPE type) {
        DepositMetadata.Person person = new DepositMetadata.Person();
        person.setFirstName(userEntity.getFirstName());
        person.setMiddleName(userEntity.getMiddleName());
        person.setLastName(userEntity.getLastName());
        person.setEmail(userEntity.getEmail());
        person.setType(type);
        // Available User data for which there is no place in the existing DepositMetadata.Person:
        // Leave commented out until this metadata is to be used.  Protect against NPEs.
//        String username = userEntity.getUsername();
//        String displayName = userEntity.getDisplayName();
//        String affiliation = userEntity.getAffiliation();
//        String institutionalId = userEntity.getInstitutionalId();
//        String localKey = userEntity.getLocalKey();
//        String orcidId = userEntity.getOrcidId();
//        for (User.Role role : userEntity.getRoles()) {
//        }

        return person;
    }

    // Creates a Person representing an author with the given name.
    private DepositMetadata.Person createAuthor(String fullName) {
        DepositMetadata.Person person = new DepositMetadata.Person();
        person.setFullName(fullName);
        person.setType(DepositMetadata.PERSON_TYPE.author);
        return person;
    }

    // Convenience method for retrieving a boolean property.  Should the default be true or false?
    private Optional<Boolean> getBooleanProperty(JsonObject parent, String name) {
        if (parent.has(name)) {
            return Optional.of(parent.get(name).getAsBoolean());
        }
        return Optional.empty();
    }

    // Convenience method for retrieving a string property.  Should the default be empty or null?
    private Optional<String> getStringProperty(JsonObject parent, String name) {
        if (parent.has(name)) {
            return Optional.of(parent.get(name).getAsString());
        }

        return Optional.empty();
    }

    // The following four methods are based on a single sample of PASS submission metadata at
    // https://github.com/OA-PASS/pass-ember/issues/194.
    private void processCommonMetadata(DepositMetadata metadata, JsonObject submissionData)
            throws InvalidModel {

        // Is this tile for manuscript or article or both?
        getStringProperty(submissionData, "title")
                .ifPresent(title -> {
                    metadata.getManuscriptMetadata().setTitle(title);
                    metadata.getArticleMetadata().setTitle(title);
        });

        getStringProperty(submissionData, "abstract")
                .ifPresent(abs -> metadata.getManuscriptMetadata().setMsAbstract(abs));

        getStringProperty(submissionData, "journal-title")
                .ifPresent(jTitle -> metadata.getJournalMetadata().setJournalTitle(jTitle));

        // Leave commented out until this metadata is to be used.  Protect against NPEs.
//        String journalTitleShort = getStringProperty(submissionData, "journal-title-short");
//        String volume = getStringProperty(submissionData, "volume");
//        String issue = getStringProperty(submissionData, "issue");
//        String subjects = getStringProperty(submissionData, "subjects");

        getStringProperty(submissionData, "URL").ifPresent(url -> {
            try {
                metadata.getManuscriptMetadata().setManuscriptUrl(new URL(url));
            } catch (MalformedURLException e) {
                InvalidModel im = new InvalidModel(String.format("Data file '%s' contained an invalid URL.", url), e);
                throw new RuntimeException(im.getMessage(), im);
            }
        });

        // Leave commented out until this metadata is to be used.  Protect against NPEs.
        JsonArray authors = submissionData.get("authors").getAsJsonArray();
        for (JsonElement element : authors) {
            JsonObject author = element.getAsJsonObject();
            getStringProperty(author, "author")
                    .ifPresent(name -> metadata.getPersons().add(createAuthor(name)));
            // String orcid = getStringProperty(author, "orcid");
        }
    }

    private void processNihMetadata(DepositMetadata metadata, JsonObject submissionData) {
        getStringProperty(submissionData, "journal-NLMTA-ID")
                .ifPresent(journalId -> metadata.getJournalMetadata().setJournalId(journalId));
        getStringProperty(submissionData, "ISSN")
                .ifPresent(issn -> metadata.getJournalMetadata().setIssn(issn));
    }

    private void processJScholarshipMetadata(DepositMetadata metadata, JsonObject submissionData)
            throws InvalidModel {
        if (! submissionData.has("under-embargo")) {
            return;
        }

        getStringProperty(submissionData, "Embargo-end-date").ifPresent(endDate -> {
            try {
                getBooleanProperty(submissionData, "under-embargo").ifPresent(underEmbargo -> {
                    if (!underEmbargo) {
                        return;
                    }

                    metadata.getArticleMetadata().setUnderEmbargo(underEmbargo);

                    getStringProperty(submissionData, "embargo").ifPresent(embargoTerms -> metadata
                            .getArticleMetadata().setEmbargoTerms(embargoTerms));

                    getBooleanProperty(submissionData, "agreement-to-embargo").ifPresent(agreement -> {
                            if (!agreement) {
                                return;
                            }
                            metadata.getArticleMetadata().setAgreementToEmbargo(agreement);
                            });

                    // TODO - Resolve inconsistent date/date-time formats in metadata and deposit data model
                    // TODO - Fix assumption of local timezone
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy");
                    LocalDateTime localEndDate = LocalDate.parse(endDate, formatter).atStartOfDay();
                    ZonedDateTime zonedEndDate = localEndDate.atZone(ZoneId.of("America/New_York"));
                    metadata.getArticleMetadata().setEmbargoLiftDate(zonedEndDate);
                });

            } catch (Exception e) {
                InvalidModel im = new InvalidModel(String.format("Data file contained an invalid Date: '%s'.",
                        endDate), e);
                throw new RuntimeException(im.getMessage(), im);
            };
        });
    }

    private void processMetadata(DepositMetadata depositMetadata, String metadataStr)
            throws InvalidModel {
        JsonArray metadataJson = new JsonParser().parse(metadataStr).getAsJsonArray();
        for (JsonElement element : metadataJson) {
            JsonObject obj = element.getAsJsonObject();
            String type = obj.get("id").getAsString();
            JsonObject data = obj.get("data").getAsJsonObject();
            if (type.equals("common")) {
                processCommonMetadata(depositMetadata, data);
            }
            else if (type.equals("nih")) {
                processNihMetadata(depositMetadata, data);
            }
            else if (type.equals("JScholarship")) {
                processJScholarshipMetadata(depositMetadata, data);
            }
        }
    }

    // Walk the tree of PassEntity objects, starting with the Submission entity,
    // to copy the desired source data into a new DepositSubmission data model.
    DepositSubmission createDepositSubmission(Submission submissionEntity, HashMap<URI, PassEntity> entities)
            throws InvalidModel {

        // The submission object to populate
        DepositSubmission submission = new DepositSubmission();

        // Prepare for Metadata
        DepositMetadata metadata = new DepositMetadata();
        submission.setMetadata(metadata);
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
        // Available Submission data for which there is no place in the existing DepositSubmission:
        // Leave commented out until this metadata is to be used.  Protect against NPEs.
//        Submission.Source source = submissionEntity.getSource();
//        Boolean submitted = submissionEntity.getSubmitted();
//        DateTime submittedDate = submissionEntity.getSubmittedDate();
//        Submission.AggregatedDepositStatus status = submissionEntity.getAggregatedDepositStatus();

        // Data from the Submission's user resource
        User userEntity = (User)entities.get(submissionEntity.getUser());
        persons.add(createPerson(userEntity, DepositMetadata.PERSON_TYPE.submitter));

        // Data from the Submission's Publication resource and its referenced Journal and Publisher resources
        Publication publicationEntity = (Publication)entities.get(submissionEntity.getPublication());
        manuscript.setTitle(publicationEntity.getTitle());
        String doi = publicationEntity.getDoi();
        try {
            if (doi != null) {
                doi = doi.trim();
                article.setDoi(new URI(doi));
            }
        } catch (URISyntaxException e) {
            String msg = String.format("Submission contained an invalid DOI URI: %s", doi);
            throw new InvalidModel(msg, e);
        }
        // Available Publication data for which there is no place in the existing deposit model:
        // Some of these properties are ignored because they are overwritten by the metadata, below.
        // Leave commented out until this metadata is to be used.  Protect against NPEs.
//        String publicationAbstract = publicationEntity.getPublicationAbstract();
//        String pmid = publicationEntity.getPmid();
//        String volume = publicationEntity.getVolume();
//        String issue = publicationEntity.getIssue();

        Journal journalEntity = (Journal)entities.get(publicationEntity.getJournal());
        //journal.setJournalTitle(journalEntity.getName());
        for (String issn : journalEntity.getIssns()) {
            // Note: the current model only has room for one ISSN, but Fedora provides multiples
            if (issn == journalEntity.getIssns().get(0)) {
                //journal.setIssn(issn);
            }
        }
        // Is the model's ID the nlmta value?
        //journal.setJournalId(journalEntity.getNlmta());
        // Available data for which there is no place in the existing deposit model:
        // Leave commented out until this metadata is to be used.  Protect against NPEs.
//        PmcParticipation journalParticipation = journalEntity.getPmcParticipation();

        // Publishers are not currently used in deposits, and may be missing from Journal resources.
        /*
        if (journalEntity.getPublisher() != null) {
            Publisher publisherEntity = (Publisher) entities.get(journalEntity.getPublisher());
            // Available data for which there is no place in the existing deposit model:
            String publisherName = publisherEntity.getName();
            PmcParticipation publisherParticipation = publisherEntity.getPmcParticipation();
        }
        */

        // As of 5/14/18, the following data is available from both the Submission metadata
        // and as a member of one of the PassEntity objects referenced by the Submission:
        //      manuscript: title, abstract, volume, issue
        //      journal: title, issn, NLMTA-ID
        // The metadata values are processed AFTER the PassEntity objects so they have precedence.
        processMetadata(metadata, submissionEntity.getMetadata());

        // Data from the Submission's Repository resources
        for (URI repositoryUri : submissionEntity.getRepositories()) {
            Repository repositoryEntity = (Repository)entities.get(repositoryUri);
            // Available Repository data for which there is no place in the existing deposit model:
            // Leave commented out until this metadata is to be used.  Protect against NPEs.
//            String repoName = repositoryEntity.getName();
//            String repoDescription = repositoryEntity.getDescription();
//            URI repoUrl = repositoryEntity.getUrl();
            // (member formSchema should not be needed)
        }

        // Data from the Grant resources
        for (URI grantUri : submissionEntity.getGrants()) {
            Grant grantEntity = (Grant)entities.get(grantUri);
            // Available data for which there is no place in the existing model:
            // Leave commented out until this metadata is to be used.  Protect against NPEs.
//            String awardNum = grantEntity.getAwardNumber();
//            Grant.AwardStatus awardStatus = grantEntity.getAwardStatus();
//            String projectLocalKey = grantEntity.getLocalKey();
//            String projectName = grantEntity.getProjectName();
//            DateTime awardDate = grantEntity.getAwardDate();
//            DateTime startDate = grantEntity.getStartDate();
//            DateTime endDate = grantEntity.getEndDate();

            // Data from the Primary Funder and its Policy resources
//            Funder primaryFunderEntity = (Funder)entities.get(grantEntity.getPrimaryFunder());
//            // Available Funder data for which there is no place in the existing deposit model:
//            String funderName = primaryFunderEntity.getName();
//            URI funderUrl = primaryFunderEntity.getUrl();
//            String funderLocalKey = primaryFunderEntity.getLocalKey();

//            Policy primaryPolicyEntity = (Policy)entities.get(primaryFunderEntity.getPolicy());
//            // Available Policy data for which there is no place in the existing deposit model:
//            String policyTitle = primaryPolicyEntity.getTitle();
//            String description = primaryPolicyEntity.getDescription();
//            URI policyUrl = primaryPolicyEntity.getPolicyUrl();
//            // Policies also have a lists of repositories, which we ignore in favor of the submission's list.

            // Data from Direct Funder and its Policy has the same properties as for the Primary Funder
            Funder directFunderEntity = (Funder)entities.get(grantEntity.getDirectFunder());
//            Policy directPolicy = (Policy)entities.get(directFunderEntity.getPolicy());

            // Data from the User resources for the PI and CoPIs
            User piEntity = (User)entities.get(grantEntity.getPi());
            persons.add(createPerson(piEntity, DepositMetadata.PERSON_TYPE.pi));
            for (URI copiUri : grantEntity.getCoPis()) {
                User copiEntity = (User)entities.get(copiUri);
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
                File file = (File)entity;
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
