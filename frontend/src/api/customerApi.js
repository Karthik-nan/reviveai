import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const customerApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

export const getCustomers = async () => {
  const response = await customerApi.get("/customers");
  return response.data;
};

export const getCustomerById = async (id) => {
  const response = await customerApi.get(`/customers/${id}`);
  return response.data;
};

export default customerApi;