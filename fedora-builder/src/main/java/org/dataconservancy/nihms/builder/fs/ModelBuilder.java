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
import com.google.gson.JsonParser;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositManifest;
import org.dataconservancy.nihms.model.DepositMetadata;
import org.dataconservancy.nihms.model.DepositSubmission;

import org.dataconservancy.pass.model.Funder;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Journal;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.PmcParticipation;
import org.dataconservancy.pass.model.Policy;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.Publisher;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.User;
import org.joda.time.DateTime;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

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
    // Note that callers currently protect against adding a person twice (as both an author and PI).
    private DepositMetadata.Person createPerson(User userEntity, boolean isPi, boolean isCoPI, boolean isAuthor) {
        DepositMetadata.Person person = new DepositMetadata.Person();
        person.setFirstName(userEntity.getFirstName());
        person.setMiddleName(userEntity.getMiddleName());
        person.setLastName(userEntity.getLastName());
        person.setEmail(userEntity.getEmail());
        person.setPi(isPi);
        person.setCorrespondingPi(isCoPI);
        person.setAuthor(isAuthor);
        // Available User data for which there is no place in the existing DepositMetadata.Person:
        String username = userEntity.getUsername();
        String displayName = userEntity.getDisplayName();
        String affiliation = userEntity.getAffiliation();
        String institutionalId = userEntity.getInstitutionalId();
        String localKey = userEntity.getLocalKey();
        String orcidId = userEntity.getOrcidId();
        for (User.Role role : userEntity.getRoles()) {
        }

        return person;
    }

    // Walk the tree of PassEntity objects, starting with the Submission entity,
    // to copy the desired source data into a new DepositSubmission data model.
    DepositSubmission createDepositSubmission(
            Submission submissionEntity, HashMap<URI, PassEntity> entities) throws URISyntaxException {

        // The submission object to populate
        DepositSubmission submission = new DepositSubmission();

        // Prepare for binary Files
        ArrayList<DepositFile> files = new ArrayList<>();
        submission.setFiles(files);

        // Prepare Manifest
        DepositManifest manifest = new DepositManifest();
        manifest.setFiles(files);
        submission.setManifest(manifest);

        // Prepare for Metadata
        DepositMetadata metadata = new DepositMetadata();
        submission.setMetadata(metadata);
        DepositMetadata.Manuscript manuscript = new DepositMetadata.Manuscript();
        metadata.setManuscriptMetadata(manuscript);
        DepositMetadata.Article article = new DepositMetadata.Article();
        metadata.setArticleMetadata(article);
        ArrayList<DepositMetadata.Person> persons = new ArrayList<>();
        metadata.setPersons(persons);

        // Data from the Submission resource
        submission.setId(submissionEntity.getId().toString());
        // The deposit data model requires a "name" - for now we use the ID.
        submission.setName(submissionEntity.getId().toString());
        JsonArray metadataJson = new JsonParser().parse(submissionEntity.getMetadata()).getAsJsonArray();
        // TODO - Extract property values from the JsonArray (or whatever JSON structure is used)
        // Available Submission data for which there is no place in the existing DepositSubmission:
        Submission.Source source = submissionEntity.getSource();
        Boolean submitted = submissionEntity.getSubmitted();
        DateTime submittedDate = submissionEntity.getSubmittedDate();
        Submission.AggregatedDepositStatus status = submissionEntity.getAggregatedDepositStatus();
        // Existing DepositSubmission members that are not being set:
        //      article.pubmedcentralid, article.pubmedid, manuscript.id, manuscript.url

        // Data from the Submission's user resource
        User userEntity = (User)entities.get(submissionEntity.getUser());
        persons.add(createPerson(userEntity, false, false, true));

        // Data from the Submission's Publication resource and its referenced Journal and Publisher resources
        Publication publicationEntity = (Publication)entities.get(submissionEntity.getPublication());
        manuscript.setTitle(publicationEntity.getTitle());
        article.setDoi(new URI(publicationEntity.getDoi()));
        // Available Publication data for which there is no place in the existing deposit model:
        String publicationAbstract = publicationEntity.getPublicationAbstract();
        String pmid = publicationEntity.getPmid();
        String volume = publicationEntity.getVolume();
        String issue = publicationEntity.getIssue();
        
        Journal journalEntity = (Journal)entities.get(publicationEntity.getJournal());
        DepositMetadata.Journal journal = new DepositMetadata.Journal();
        metadata.setJournalMetadata(journal);
        journal.setJournalTitle(journalEntity.getName());
        for (String issn : journalEntity.getIssns()) {
            // Note: the current model only has room for one ISSN, but Fedora provides multiples
            if (issn == journalEntity.getIssns().get(0)) {
                journal.setIssn(issn);
            }
        }
        // Is the model's ID the nlmta value?
        journal.setJournalId(journalEntity.getNlmta());
        // Available data for which there is no place in the existing deposit model:
        PmcParticipation journalParticipation = journalEntity.getPmcParticipation();

        Publisher publisherEntity = (Publisher)entities.get(journalEntity.getPublisher());
        // Available data for which there is no place in the existing deposit model:
        String publisherName = publisherEntity.getName();
        PmcParticipation publisherParticipation = publisherEntity.getPmcParticipation();

        // Data from the Submission's Repository resources
        for (URI repositoryUri : submissionEntity.getRepositories()) {
            Repository repositoryEntity = (Repository)entities.get(repositoryUri);
            // Available Repository data for which there is no place in the existing deposit model:
            String repoName = repositoryEntity.getName();
            String repoDescription = repositoryEntity.getDescription();
            URI repoUrl = repositoryEntity.getUrl();
            // (member formSchema should not be needed)
        }

        // Data from the Grant resources
        for (URI grantUri : submissionEntity.getGrants()) {
            Grant grantEntity = (Grant)entities.get(grantUri);
            // Available data for which there is no place in the existing model:
            String awardNum = grantEntity.getAwardNumber();
            Grant.AwardStatus awardStatus = grantEntity.getAwardStatus();
            String projectLocalKey = grantEntity.getLocalKey();
            String projectName = grantEntity.getProjectName();
            DateTime awardDate = grantEntity.getAwardDate();
            DateTime startDate = grantEntity.getStartDate();
            DateTime endDate = grantEntity.getEndDate();

            // Data from the Primary Funder and its Policy resources
            Funder primaryFunderEntity = (Funder)entities.get(grantEntity.getPrimaryFunder());
            // Available Funder data for which there is no place in the existing deposit model:
            String funderName = primaryFunderEntity.getName();
            URI funderUrl = primaryFunderEntity.getUrl();
            String funderLocalKey = primaryFunderEntity.getLocalKey();

            Policy primaryPolicyEntity = (Policy)entities.get(primaryFunderEntity.getPolicy());
            // Available Policy data for which there is no place in the existing deposit model:
            String policyTitle = primaryPolicyEntity.getTitle();
            String description = primaryPolicyEntity.getDescription();
            URI policyUrl = primaryPolicyEntity.getPolicyUrl();
            // Policies also have a lists of repositories, we we ignore in favor of the submission's list.

            // Data from Direct Funder and its Policy has the same properties as for the Primary Funder
            Funder directFunderEntity = (Funder)entities.get(grantEntity.getDirectFunder());
            Policy directPolicy = (Policy)entities.get(directFunderEntity.getPolicy());

            // Data from the User resources for the PI and CoPIs
            User piEntity = (User)entities.get(grantEntity.getPi());
            persons.add(createPerson(piEntity, true, false, false));
            for (URI copiUri : grantEntity.getCoPis()) {
                User copiEntity = (User)entities.get(copiUri);
                persons.add(createPerson(copiEntity, false, true, false));
            }
        }

        // Add Files
        // TODO: We do not yet search the repository for Files that reference this Submission.

        return submission;
    }
}
