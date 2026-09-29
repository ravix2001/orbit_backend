package com.ravi.orbit.utils;

import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.exceptions.BadRequestException;

public class CommonValidator {

    public static void validatePhoneNo(String phoneNumber) {
        String strippedPhoneNumber = phoneNumber.replaceAll("[^\\d]", "");

        if (CommonMethods.isEmpty(phoneNumber) ||
                strippedPhoneNumber.length() < 8 || strippedPhoneNumber.length() > 15) {
            throw new BadRequestException(MyConstants.ERR_MSG_BAD_REQUEST + "Phone");
        }
    }

    public static void validateEmail(String email) {
        if (CommonMethods.isEmpty(email)) {
            throw new BadRequestException("Email cannot be empty");
        }
        if (!email.matches(MyConstants.RE_EMAIL)) {
            throw new BadRequestException(MyConstants.ERR_MSG_BAD_REQUEST + "Email");
        }
    }

    public static void validateAuth(AuthDTO authDTO) {
        if (CommonMethods.isEmpty(authDTO.getUsername())) {
            throw new BadRequestException("Username cannot be empty");
        }
        if (CommonMethods.isEmpty(authDTO.getPassword())) {
            throw new BadRequestException("Password cannot be empty");
        }
    }

    public static void validateDevice(String deviceId, String deviceName) {
        if (CommonMethods.isEmpty(deviceId)) {
            throw new BadRequestException("DeviceId cannot be empty");
        }
        if (CommonMethods.isEmpty(deviceName)) {
            throw new BadRequestException("DeviceName cannot be empty");
        }
    }


}
