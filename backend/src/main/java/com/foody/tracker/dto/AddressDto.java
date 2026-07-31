package com.foody.tracker.dto;

import com.foody.tracker.entity.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @NotBlank @Size(max = 120) String street,
        @NotBlank @Size(max = 20) String number,
        @Size(max = 120) String complement,
        @NotBlank @Size(max = 120) String district,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 60) String state,
        @NotBlank @Size(max = 20) String zipCode) {

    public Address toAddress() {
        return new Address(street, number, complement, district, city, state, zipCode);
    }

    public static AddressDto from(Address address) {
        return new AddressDto(address.getStreet(), address.getNumber(), address.getComplement(),
                address.getDistrict(), address.getCity(), address.getState(), address.getZipCode());
    }
}
