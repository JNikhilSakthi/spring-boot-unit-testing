# Unit Testing Project — API Testing Guide

Base URL: `http://localhost:8090`

---

## 1. User APIs

### Create User
**POST** `/api/users`

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "9876543210",
  "role": "ADMIN"
}
```

```json
{
  "firstName": "Sarah",
  "lastName": "Smith",
  "email": "sarah.smith@example.com",
  "phone": "8765432109",
  "role": "USER"
}
```

```json
{
  "firstName": "Mike",
  "lastName": "Johnson",
  "email": "mike.j@example.com",
  "phone": "7654321098",
  "role": "ADMIN"
}
```

### Get All Users
**GET** `/api/users`

### Get User by ID
**GET** `/api/users/1`

### Get User by Email
**GET** `/api/users/email/john.doe@example.com`

### Get Users by Role
**GET** `/api/users/role/ADMIN`

### Search Users by First Name
**GET** `/api/users/search?firstName=John`

### Update User
**PUT** `/api/users/1`

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.updated@example.com",
  "phone": "9876543210",
  "role": "ADMIN"
}
```

### Delete User
**DELETE** `/api/users/1`

---

## 2. Product APIs

> Create Users first — Products require a valid `userId` for `createdBy` / `updatedBy`.

### Create Product
**POST** `/api/products`

```json
{
  "name": "iPhone 16 Pro",
  "description": "Latest Apple smartphone with A18 chip",
  "price": 134900.00,
  "quantity": 50,
  "category": "Electronics",
  "userId": 1
}
```

```json
{
  "name": "Nike Air Max 270",
  "description": "Comfortable running shoes",
  "price": 12995.00,
  "quantity": 200,
  "category": "Footwear",
  "userId": 2
}
```

```json
{
  "name": "Samsung Galaxy S25 Ultra",
  "description": "Flagship Android phone with S Pen",
  "price": 129999.00,
  "quantity": 75,
  "category": "Electronics",
  "userId": 1
}
```

```json
{
  "name": "Sony WH-1000XM5",
  "description": "Noise cancelling wireless headphones",
  "price": 29990.00,
  "quantity": 120,
  "category": "Audio",
  "userId": 3
}
```

### Get All Products
**GET** `/api/products`

### Get Product by ID
**GET** `/api/products/1`

### Get Products by Category
**GET** `/api/products/category/Electronics`

### Search Products by Name
**GET** `/api/products/search?name=iPhone`

### Get Products by Price Range
**GET** `/api/products/price-range?min=10000&max=50000`

### Update Product
**PUT** `/api/products/1`

```json
{
  "name": "iPhone 16 Pro Max",
  "description": "Updated to Pro Max variant",
  "price": 159900.00,
  "quantity": 30,
  "category": "Electronics",
  "userId": 2
}
```

> Note: On update, `updatedBy` changes to userId 2 (Sarah), while `createdBy` stays as userId 1 (John).

### Delete Product
**DELETE** `/api/products/1`

---

## 3. Error Scenarios to Test

### 404 — Resource Not Found
**GET** `/api/users/999`
**GET** `/api/products/999`

### 409 — Duplicate Email
**POST** `/api/users`
```json
{
  "firstName": "Duplicate",
  "lastName": "User",
  "email": "john.doe@example.com",
  "phone": "1111111111",
  "role": "USER"
}
```

### 400 — Validation Errors

**POST** `/api/users` (blank fields)
```json
{
  "firstName": "",
  "lastName": "",
  "email": "not-an-email",
  "phone": "",
  "role": ""
}
```

**POST** `/api/products` (missing required fields)
```json
{
  "name": "",
  "description": "",
  "price": -10,
  "quantity": -1,
  "category": "",
  "userId": null
}
```

---

## 4. Recommended Testing Order

1. POST 3 Users
2. POST 4 Products
3. GET all Users / Products
4. GET by ID, email, category, search, price-range
5. PUT update a User and a Product
6. Test error scenarios (404, 409, 400)
7. DELETE a User and a Product
