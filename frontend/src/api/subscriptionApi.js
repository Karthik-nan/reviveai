import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const subscriptionApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

export const getSubscriptions = async () => {
  const response = await subscriptionApi.get("/subscriptions");
  return response.data;
};

export const getSubscriptionById = async (id) => {
  const response = await subscriptionApi.get(
    `/subscriptions/${id}`
  );

  return response.data;
};

export default subscriptionApi;