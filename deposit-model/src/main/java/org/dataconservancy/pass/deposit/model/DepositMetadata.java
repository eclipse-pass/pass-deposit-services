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

package org.dataconservancy.pass.deposit.model;

import java.net.URI;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates required and optional metadata for submitting a manuscript.
 */
public class DepositMetadata {

    /**
     * ISSN associated with a publication type
     */
    public static class IssnPubType {

        public String issn;

        public JournalPublicationType pubType;

        public IssnPubType(String issn, JournalPublicationType pubType) {
            this.issn = issn;
            this.pubType = pubType;
        }

        @Override
        public String toString() {
            return "IssnPubType{" + "issn='" + issn + '\'' + ", pubType=" + pubType + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IssnPubType that = (IssnPubType) o;

            if (!issn.equals(that.issn)) {
                return false;
            }
            return pubType == that.pubType;
        }

        @Override
        public int hashCode() {
            int result = issn.hashCode();
            result = 31 * result + pubType.hashCode();
            return result;
        }
    }

    /**
     * Person type: identifies person's role as submitter, PI/co-PI or author
     */
    public enum PERSON_TYPE {
        /**
         * submitter (from Submission)
         */
        submitter,

        /**
         * co-PI (from Grant)
         */
        pi,

        /**
         * co-PI (from Grant)
         */
        copi,

        /**
         * author (from submission metadata)
         */
        author,
    }

    /**
     * Metadata describing the manuscript
     */
    private Manuscript manuscriptMetadata;

    /**
     * Metadata describing the journal the manuscript is being published in
     */
    private Journal journalMetadata;

    /**
     * Metadata describing the people associated with the submission, and their roles
     */
    private List<Person> persons;

    /**
     * Metadata about the published article (??)
     */
    private Article articleMetadata;

    /**
     * Manuscript-related metadata fields
     */
    public static class Manuscript {

        /**
         * Internal NIHMS submission ID; {@code null} unless this is a re-submission
         */
        public String nihmsId;

        /**
         * URL to the manuscript, if known.
         */
        public URL manuscriptUrl;

        /**
         * {@code true} if the submission includes the the publisher's final PDF version of the manuscript
         */
        public boolean publisherPdf;

        /**
         * {@code true} if {@link #publisherPdf} is {@code true} <em>and</em> if the publisher's final PDF version
         * should be used in lieu of the NIHMS-generated PDF in PubMed Central.
         */
        public boolean showPublisherPdf;

        /**
         * The title of the manuscript
         */
        public String title;

        /**
         * A brief abstract of the manuscript
         */
        public String msAbstract;

        public String getMsAbstract() {
            return msAbstract;
        }

        public void setMsAbstract(String msAbstract) {
            this.msAbstract = msAbstract;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getNihmsId() {
            return nihmsId;
        }

        public void setNihmsId(String nihmsId) {
            this.nihmsId = nihmsId;
        }

        public URL getManuscriptUrl() {
            return manuscriptUrl;
        }

        public void setManuscriptUrl(URL manuscriptUrl) {
            this.manuscriptUrl = manuscriptUrl;
        }

        public boolean isPublisherPdf() {
            return publisherPdf;
        }

        public void setPublisherPdf(boolean publisherPdf) {
            this.publisherPdf = publisherPdf;
        }

        public boolean isShowPublisherPdf() {
            return showPublisherPdf;
        }

        public void setShowPublisherPdf(boolean showPublisherPdf) {
            this.showPublisherPdf = showPublisherPdf;
        }

    }

    /**
     * Journal-related metadata fields
     */
    public static class Journal {

        /**
         * NLM title abbreviation
         */
        public String journalId;

        /**
         * Journal type, always set to {@literal nlm-ta}
         */
        public String journalType = "nlm-ta";

        /**
         * NLM full journal title
         */
        public String journalTitle;

        /**
         * Name of publisher
         */
        public String publisherName;

        /**
         * Date of publication
         */
        public String publicationDate;

        /**
         * ISSN mapped to journal publication type
         */
        public Map<String, IssnPubType> issnPubTypes = new HashMap<>(2);

        /**
         * Serial number for the journal
         */
        @Deprecated
        public String issn;

        public String getJournalId() {
            return journalId;
        }

        public void setJournalId(String journalId) {
            this.journalId = journalId;
        }

        public String getJournalType() {
            return journalType;
        }

        public String getJournalTitle() {
            return journalTitle;
        }

        public void setJournalTitle(String journalTitle) {
            this.journalTitle = journalTitle;
        }

        public String getPublisherName() {
            return publisherName;
        }

        public void setPublisherName(String publisherName) {
            this.publisherName = publisherName;
        }

        public String getPublicationDate() {
            return publicationDate;
        }

        public void setPublicationDate(String publicationDate) {
            this.publicationDate = publicationDate;
        }

        public Map<String, IssnPubType> getIssnPubTypes() {
            return issnPubTypes;
        }

        public void setIssnPubTypes(Map<String, IssnPubType> issnPubTypes) {
            this.issnPubTypes = issnPubTypes;
        }

        @Deprecated
        public String getIssn() {
            return issn;
        }

        @Deprecated
        public void setIssn(String issn) {
            this.issn = issn;
        }
    }

    // TODO: filled in by submitter or for NIHMS?
    public static class Article {
        /**
         * DOI for the final version of the article, if known.
         */
        public URI doi;

        /**
         * The publication volume in which the article appears
         */
        public String volume;

        /**
         * The publication's issue in which the article appears
         */
        public String issue;

        /**
         * The title of the article
         */
        public String title;

        public ZonedDateTime embargoLiftDate = null;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getVolume() {
            return volume;
        }

        public void setVolume(String volume) {
            this.volume = volume;
        }

        public String getIssue() {
            return issue;
        }

        public void setIssue(String issue) {
            this.issue = issue;
        }

        public URI getDoi() {
            return doi;
        }

        public void setDoi(URI doi) {
            this.doi = doi;
        }

        public ZonedDateTime getEmbargoLiftDate() {
            return embargoLiftDate;
        }

        public void setEmbargoLiftDate(ZonedDateTime embargoLiftDate) {
            this.embargoLiftDate = embargoLiftDate;
        }
    }

    /**
     * A Person associated with the submission, their names and role
     */
    public static class Person {

        public String fullName;

        public String firstName;

        public String middleName;

        public String lastName;

        public String email;

        /**
         * The role for this person.  People with multiple roles are represented with multiple Person objects.
         */
        public PERSON_TYPE type;

        public Person() {
        }

        public Person(Person otherPerson) {
            this.setFullName(otherPerson.getFullName());
            this.setFirstName(otherPerson.getFirstName());
            this.setMiddleName(otherPerson.getMiddleName());
            this.setLastName(otherPerson.getLastName());
            this.setType(otherPerson.getType());
        }

        /**
         * Returns the assembled name for the person using the first/middle/last names.
         *
         * @return the complete name for the person, or empty string if first or last are missing.
         */
        public String getConstructedName() {
            if (getFirstName() != null && getLastName() != null) {
                if (getMiddleName() != null) {
                    return String.format("%s %s %s", getFirstName(), getMiddleName(), getLastName());
                } else {
                    return String.format("%s %s", getFirstName(), getLastName());
                }
            }
            return "";
        }

        /**
         * Returns the "total" name for the person, regardless of how that name was supplied.
         * If a "full" name (single string) was supplied, it will be returned.
         * Otherwise, a name will be constructed from the supplied first/middle/last names.
         *
         * @return the complete name for the person, or empty string if none assigned.
         */
        public String getName() {
            if (getFullName() != null) {
                return getFullName();
            } else {
                return getConstructedName();
            }
        }

        /**
         * Returns the last-name-first version of the "total" name for a person
         * whose name was supplied as a combination of a first, a last and an optional middle name.
         *
         * @return the complete name for the person, or empty string if none assigned.
         */
        public String getReversedName() {
            if (getFirstName() != null && getLastName() != null) {
                if (getMiddleName() != null) {
                    return String.format("%s, %s %s", getLastName(), getFirstName(), getMiddleName());
                } else {
                    return String.format("%s, %s", getLastName(), getFirstName());
                }
            }
            return getFullName();
        }

        /**
         * Returns the supplied "full" name string for the person.
         *
         * @return the supplied name string or null if none assigned.
         */
        public String getFullName() {
            return fullName;
        }

        /**
         * Stores a single string containing the person's entire name.
         *
         * @param fullName the person's entire name
         */
        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getMiddleName() {
            return middleName;
        }

        public void setMiddleName(String middleName) {
            this.middleName = middleName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public PERSON_TYPE getType() {
            return type;
        }

        public void setType(PERSON_TYPE type) {
            this.type = type;
        }
    }

    public DepositMetadata() {
        this.manuscriptMetadata = new Manuscript();
        this.journalMetadata = new Journal();
        this.articleMetadata = new Article();
        this.persons = new ArrayList<>();
    }

    public Manuscript getManuscriptMetadata() {
        return manuscriptMetadata;
    }

    public void setManuscriptMetadata(Manuscript manuscriptMetadata) {
        this.manuscriptMetadata = manuscriptMetadata;
    }

    public Journal getJournalMetadata() {
        return journalMetadata;
    }

    public void setJournalMetadata(Journal journalMetadata) {
        this.journalMetadata = journalMetadata;
    }

    public List<Person> getPersons() {
        return persons;
    }

    public void setPersons(List<Person> persons) {
        this.persons = persons;
    }

    public Article getArticleMetadata() {
        return articleMetadata;
    }

    public void setArticleMetadata(Article articleMetadata) {
        this.articleMetadata = articleMetadata;
    }

    @Override
    public String toString() {
        return "DepositMetadata{" +
               "manuscriptMetadata=" + manuscriptMetadata +
               ", journalMetadata=" + journalMetadata +
               ", persons=" + persons +
               ", articleMetadata=" + articleMetadata +
               '}';
    }

}
