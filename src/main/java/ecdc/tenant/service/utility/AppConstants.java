package ecdc.tenant.service.utility;

public interface AppConstants {
    /**
     * Context paths
     */
    String AUTH_CONTEXT_PATH = "/api/v1/auth/";
    String TENANT_CONTEXT_PATH = "/api/v1/tenants/";


    /**
     * Messages
     */
    String SUCCESS_MESSAGE = "Request completed successfully!";
    String SAVED_MESSAGE = "Record saved successfully";
    String SAVED_LIST_MESSAGE = "Records saved successfully";
    String UPDATED_MESSAGE = "Record updated successfully";
    String UPDATED_LIST_MESSAGE = "Records updated successfully";
    String FETCH_SUCCESS_MESSAGE = "Records retrieved successfully!";
    String FETCH_SUCCESS_MESSAGE_BY_ID = "Record successfully retrieved by id";
    String NO_RECORD_FOUND = "No record found!";
    String NO_RECORD_FOUND_OR_DISABLED = "No record found or disabled!";
    String DELETED_MESSAGE = "Record deleted successfully";
    String DELETED_LIST_MESSAGE = "Records deleted successfully";
    String ERROR_MESSAGE = "Error Occurred!";
    String NON_NULL_OR_EMPTY = "Cannot be null or empty";
    String NO_AUTHORIZATION_MESSAGE = "No authorization to perform this action";
    String LDAP_ERROR = "Error authenticating on ldap : Invalid username or password provided.";
    String LDAP_USER_QUERY = "firstName,lastName,email,mobile,username,fullName,gender";
    String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    String USER_EXISTS = "User already exists";
    String CLIENT_EXISTS = "Client already exists with this name";
    String ROLE_EXISTS = "Role already exists with this name";
    String PERMISSION_EXISTS = "Permission already exists with this name ";
    String PLATFORM_EXISTS = "Platform already exists with this name ";
    String GENERAL_CODE_EXISTS = "General code already exists with this code name ";
    String GENERAL_CODE_TYPE_EXISTS = "General code type already exists with this code name ";
    String ACTION_SUCCESS_MESSAGE = "Action successful!";
    String DATE_VALIDATION = "Start Date cannot be after End Date";
    String UPLOAD_VALIDATION = "Fill all fields to complete upload";


    /**
     * Pagination filtering and sorting params
     */
    String PARAM_PAGINATE = "paginate";
    String PARAM_PAGE_NO = "page";
    String PARAM_PAGE_SIZE = "size";
    String PARAM_SORT_BY = "sortBy";
    String PARAM_SORT_DIR = "sortDir";
    String PARAM_SEARCH = "search";
    String PARAM_STATUS = "status";
    String PARAM_NAME = "name";
    String PARAM_TYPE = "type";
    String PARAM_USER_ID = "userId";
    String START_DATE = "startDate";
    String END_DATE =  "endDate";
    String CLIENT_TYPE =  "clientType";
    String CODE_TYPE =  "codeType";


    /**
     * Default values
     */
    Integer MAX_PAGE_SIZE_API = 100;
    String DEFAULT_PAGE_SIZE = "25";
    String DEFAULT_PAGE_SORT_DIR = "asc";
    String DEFAULT_PAGE_SORT_BY = "createdAt";
    String DEFAULT_PAGE_NUMBER = "1";
    String DEFAULT_PAGINATE = "true";
    String DEFAULT_SEARCH = "";
    String DEFAULT_STATUS = "1";
    String DEFAULT_TRANSACTION_TYPE_TP = "PAYMENT";
    String DEFAULT_TRANSACTION_TYPE_WEB = "Linkyfi Pay Bill";
    String DEFAULT_TRANSACTION_TYPE_APP = "check_balance";
    String DEFAULT_FULFILMENT_TYPE = "14";
    String DEFAULT_PAYMENT_PROCESSOR = "PAYSTACK";
    String DEFAULT_PAYMENT_TYPE = "PAYMENT";
    String DEFAULT_PAYMENT_CHANNEL = "";
    String DEFAULT_CHANNEL_ID = "web";
    String DEFAULT_FBB_STATUS = "0";
    String DEFAULT_PAYMENT_STATUS = "FAILED";
    String DEFAULT_CLIENT_TYPE = "payment";
    String DEFAULT_START_DATE = "2025-07-07";
    String DEFAULT_END_DATE = "2025-08-08";


}
