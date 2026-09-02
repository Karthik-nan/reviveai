import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const policyApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

export const getPolicies = async () => {
  const response = await policyApi.get("/policies");
  return response.data;
};

export default policyApi;