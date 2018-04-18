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

import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositManifest;
import org.dataconservancy.nihms.model.DepositMetadata;
import org.dataconservancy.nihms.model.DepositSubmission;

import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Funder;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Journal;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Person;
import org.dataconservancy.pass.model.Policy;
import org.dataconservancy.pass.model.Publisher;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Workflow;

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
    // This method is intended to allow authors to be added as people.  Unfortunately, the author
    // name information is incomplete and we don't know if they are PIs (they probably are).
    // Note that there is currently protection for adding a person twice as an author and then PI.
    private DepositMetadata.Person createDepositAuthor(final String name, final String email) {
        final DepositMetadata.Person person = new DepositMetadata.Person();
        person.setFirstName(name);
        person.setEmail(email);
        person.setAuthor(true);

        return person;
    }

    // This method is intended to allow PIs and CoPIs to be added from the data with their status
    // as an author being provided through a parameter.  We don't have accurate info for that value.
    private DepositMetadata.Person createDepositPerson(final Person personEntity, final boolean isPi,
                                                       final boolean isCopi) {
        final DepositMetadata.Person person = new DepositMetadata.Person();
        person.setFirstName(personEntity.getFirstName());
        person.setMiddleName(personEntity.getMiddleName());
        person.setLastName(personEntity.getLastName());
        person.setEmail(personEntity.getEmail());
        person.setAuthor(false);
        person.setPi(isPi);
        person.setCorrespondingPi(isCopi);

        return person;
    }

    // Create and populate a DepositMetadata.Journal object from the suupplied Journal entity
    // and its subsidiary Publisher entity, then return the created Journal object.
    private DepositMetadata.Journal createDepositJournal(final Journal journalEntity,
                                                         final HashMap<URI, PassEntity> entities) {
        final DepositMetadata.Journal journal = new DepositMetadata.Journal();
        journal.setJournalTitle(journalEntity.getName());
        // Note: the current model only has room for one ISSN, but Fedora provides multiples
        if (journalEntity.getIssns().size() > 0) {
            journal.setIssn(journalEntity.getIssns().get(0));
        }
        // Is the model's ID the nlmta value, the ID (a Fedora URI), or something else?
        journal.setJournalId(journalEntity.getNlmta());
        // Available data for which there is no place in the existing model:
        //      pmcParticipation (enum), id

        // PUBLISHER
        final Publisher publisherEntity = (Publisher)entities.get(journalEntity.getPublisher());
        // Available data for which there is no place in the existing model:
        //      id, name, pcmParticipation (enum - the same as for the journal?)

        return journal;
    }

    // Walk the tree of PassEntity objects, starting with the Submission entity,
    // to copy the desired source data into a new DepositSubmission data model.
    DepositSubmission createDepositSubmission(
            final Submission submissionEntity, final HashMap<URI, PassEntity> entities) throws URISyntaxException {

        // The submission object to populate
        final DepositSubmission submission = new DepositSubmission();

        // Prepare for binary Files
        // Note: The are currently no files provided by the Fedora data model
        final ArrayList<DepositFile> files = new ArrayList<>();
        submission.setFiles(files);

        // Prepare Manifest
        final DepositManifest manifest = new DepositManifest();
        manifest.setFiles(files);
        submission.setManifest(manifest);

        // Prepare for Metadata
        final DepositMetadata metadata = new DepositMetadata();
        submission.setMetadata(metadata);
        final DepositMetadata.Manuscript manuscript = new DepositMetadata.Manuscript();
        metadata.setManuscriptMetadata(manuscript);
        final DepositMetadata.Article article = new DepositMetadata.Article();
        metadata.setArticleMetadata(article);
        final ArrayList<DepositMetadata.Person> persons = new ArrayList<>();
        metadata.setPersons(persons);

        // Data from the Submission resource
        submission.setId(submissionEntity.getId().toString());
        manuscript.setTitle(submissionEntity.getTitle());
        article.setDoi(new URI(submissionEntity.getDoi()));
        persons.add(createDepositAuthor(submissionEntity.getCorrAuthorName(),
                submissionEntity.getCorrAuthorEmail()));
        // The deposit model requires a name - for now we use the ID.
        submission.setName(submissionEntity.getId().toString());
        // Available data for which there is no place in the existing model:
        //      status (enum), abstract, submitted date, source (enum), volume, issue
        // Existing model members that are no longer set:
        //      article.pubmedcentralid, article.pubmedid, manuscript.id, manuscript.url

        // Data from the Journal and Publisher resources
        final Journal journalEntity = (Journal)entities.get(submissionEntity.getJournal());
        final DepositMetadata.Journal journal = createDepositJournal(journalEntity, entities);
        metadata.setJournalMetadata(journal);

        // Data from the Deposit resources
        for (URI depositUri : submissionEntity.getDeposits()) {
            final Deposit depositEntity = (Deposit)entities.get(depositUri);
            // Available data for which there is no place in the existing model:
            //      id, status (enum), assigned ID, access URL, requested?, user action required?
            final Repository repositoryEntity = (Repository)entities.get(depositEntity.getRepository());
            // Available data for which there is no place in the existing model:
            //      id, name, description, url
        }

        // Data from the Grant resources
        for (URI grantUri : submissionEntity.getGrants()) {
            final Grant grantEntity = (Grant)entities.get(grantUri);
            // Available data for which there is no place in the existing model:
            //      id, award number, award status (enum), local award ID, project name
            //      award date, start date, end date

            // Data from the Funder and Policy resources
            final Funder primaryFunderEntity = (Funder)entities.get(grantEntity.getPrimaryFunder());
            // Available data for which there is no place in the existing model:
            //      id, name, url, localID
            final Policy primaryPolicy = (Policy)entities.get(primaryFunderEntity.getPolicy());
            // Available data for which there is no place in the existing model:
            //      id, title, description, isDefault.
            // Policies also have a list of links to repositories.  Use these instead of link in the Grant?
            // Direct Funder and its Policy have the same properties as the Primary Funder
            final Funder directFunderEntity = (Funder)entities.get(grantEntity.getDirectFunder());
            final Policy directPolicy = (Policy)entities.get(directFunderEntity.getPolicy());

            // Data from the People resources for the PI and CoPIs
            final Person piEntity = (Person)entities.get(grantEntity.getPi());
            persons.add(createDepositPerson(piEntity, true, false));
            for (URI copiUri : grantEntity.getCoPis()) {
                final Person copiEntity = (Person)entities.get(copiUri);
                persons.add(createDepositPerson(copiEntity, false, true));
            }
        }

        // Data from the Workflow resources
        for (URI workflowUri : submissionEntity.getWorkflows()) {
            final Workflow workflowEntity = (Workflow)entities.get(workflowUri);
            // Available data for which there is no place in the existing model:
            //      id, name, step, steps
        }

        return submission;
    }
}
