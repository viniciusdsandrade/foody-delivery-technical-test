package com.foody.tracker.dto;

import com.foody.tracker.entity.Address;
import jakarta.validation.constraints.NotBlank;

public record AddressDto(
        @NotBlank String street,
        @NotBlank String number,
        String complement,
        @NotBlank String district,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String zipCode) {

    public Address toAddress() {
        return new Address(street, number, complement, district, city, state, zipCode);
    }

    public static AddressDto from(Address address) {
        return new AddressDto(address.getStreet(), address.getNumber(), address.getComplement(),
                address.getDistrict(), address.getCity(), address.getState(), address.getZipCode());
    }
}
