package com.is2c.hrm.org.domain;

import org.hibernate.annotations.SQLRestriction;

import com.is2c.hrm.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "company")
@SQLRestriction("deleted_at IS NULL")
public class Company extends SoftDeletableEntity {

    @Column(nullable = false)
    private String code;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "registration_no")
    private String registrationNo;

    private String address;
    private String phone;
    private String email;

    @Column(nullable = false)
    private String currency;

    @Column(name = "epf_employer_no")
    private String epfEmployerNo;

    @Column(name = "etf_no")
    private String etfNo;

    @Column(name = "statutory_pack", nullable = false)
    private String statutoryPack;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    protected Company() {
    }

    public Company(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(String registrationNo) {
        this.registrationNo = registrationNo;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getEpfEmployerNo() {
        return epfEmployerNo;
    }

    public void setEpfEmployerNo(String epfEmployerNo) {
        this.epfEmployerNo = epfEmployerNo;
    }

    public String getEtfNo() {
        return etfNo;
    }

    public void setEtfNo(String etfNo) {
        this.etfNo = etfNo;
    }

    public String getStatutoryPack() {
        return statutoryPack;
    }

    public void setStatutoryPack(String statutoryPack) {
        this.statutoryPack = statutoryPack;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
