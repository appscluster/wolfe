--
-- Created by IntelliJ IDEA.
-- User: riedel
-- Date: 02/11/15
-- Time: 18:44
-- To change this template use File | Settings | File Templates.
--
require "nn"
require "nngraph"

local ParamAccess, parent = torch.class('wolfe.ParamAccess', 'nn.Module')

-- path is an array that indicates which elements of the path are fixed and which are input
-- if a path element is "?", its value depends on the input, otherwise its fixed to be the given integer.
-- we also use 0 offsets because this is consistent with scala
-- e.g.: {0, "?", 3, "?"} will represent the path {1, input[1], 4, input[2]}
function ParamAccess:__init(path)
    parent.__init(self)

    --self.dims = dims
    --self.weight = weight

    self.templatePath = path
    self.inputIndices = {}
    self.constantIndices = {}
    self.currentPath = {}
    for k, v in pairs(path) do
        if v == "?" then
            table.insert(self.inputIndices, k)
        else
            table.insert(self.constantIndices, k)
            self.currentPath[k] = v + 1
        end
    end

--    print("---")
--    print(path)
--    print(self.currentPath)
--    print(self.inputIndices)
    --self:reset()
    --self:initWeights()
end

function ParamAccess:initWeight(dims)
    self.dims = dims
    self.weight = createNestedTable(self.dims)
end

function createNestedTable(dims)
    if torch.type(dims) == "torch.LongStorage" then
        return torch.Tensor(dims)
    else
        local result = {}
        for k, v in pairs(dims) do
            result[k] = createNestedTable(v)
        end
        return result
    end
end

function resetNestedTable(data, stdv)
    if torch.type(data) == "torch.DoubleTensor" then
        if stdv then
            stdv = stdv * math.sqrt(3)
        else
            stdv = 0.1 --1. / math.sqrt(data:size(2))
        end
        if nn.oldSeed then
            for i = 1, data:size(1) do
                data:select(1, i):apply(function()
                    return torch.uniform(-stdv, stdv)
                end)
            end
        else
            data:uniform(-stdv, stdv)
        end
    else
        for _, v in pairs(data) do
            resetNestedTable(v, stdv)
        end
    end
end

function ParamAccess:reset(stdv)
    resetNestedTable(self.weight, stdv)
end

local function tieNestedTable(owner, sharer)
    if torch.type(owner) == "torch.DoubleTensor" then
        sharer:set(owner)
    else
        for k, v in pairs(owner) do
            tieNestedTable(v, sharer[k])
        end
    end
end

function ParamAccess:shareWeight(other)
    other.weight = self.weight
    other.dims = self.dims
    --tieNestedTable(self.weight, other.weight)
end

function getValue(parent, path, index)
    if (path[index] == nil) then
        return parent
    else
        return getValue(parent[path[index]], path, index + 1)
    end
end

function ParamAccess:updatePath(input)
--    print("Update Path")
--    print(self.inputIndices)
--    print(self.currentPath)
--
--    print(input)
    local wrapped = {}
    if type(input) ~= "table" then
        wrapped[1] = input
    else
        wrapped = input
    end
    for k, v in pairs(self.inputIndices) do
        self.currentPath[v] = wrapped[k] + 1
    end
end

function ParamAccess:updateOutput(input)

--    print("---")
--    print(input)
--    print(self.weight)

    self:updatePath(input)
--    print(self.currentPath)
--    print(self.inputIndices)

    self.output = getValue(self.weight, self.currentPath, 1)

    print("Output:")
    print(self.output)
    print(self.weight)
    print(self.currentPath)

    return self.output
end

function ParamAccess:updateGradInput(input, gradOutput)
    if self.gradInput then

        if #self.inputIndices > 0 then
            self.gradInput = {}
            for k, _ in pairs(self.inputIndices) do
                self.gradInput[k] = torch.Tensor()
            end
        else
            self.gradInput = torch.Tensor()
        end

        return self.gradInput
    end
end


local function addNestedTable(target, scale, toAdd)
    if torch.type(toAdd) == "torch.DoubleTensor" then
        if torch.type(target) == "torch.DoubleTensor" then
            target:add(scale, toAdd)
        else
            target = toAdd:clone()
            target:mul(scale)
        end
    else
        for k, v in pairs(target) do
            addNestedTable(v, scale, toAdd[k])
        end
    end
end


function ParamAccess:parameters()
    error("ParamAccess does not support the parameters() function")
    --todo: this is problematic because the gradient is a sub-tree but the parameter isn't
    return { table.flatten(self.weight), table.flatten(self.gradWeight) }
end

function ParamAccess:zeroGradParameters()
    self.gradWeight:zero()
end

function ParamAccess:updateParameters(learningRate)
    local current = getValue(self.weight, self.currentPath, 1)
    addNestedTable(current, -learningRate, self.gradWeight)
end

function ParamAccess:accGradParameters(input, gradOutput, scale)
    --print("accGradParameters")
    scale = scale or 1

    -- need to add grad output to the sub table corresponding to the path
    self:updatePath(input)

    if self.gradWeight == nil then
        local subdims = getValue(self.dims, self.currentPath, 1)
        self.gradWeight = createNestedTable(subdims)
    end

    addNestedTable(self.gradWeight, scale, gradOutput)

    --todo: when/how are gradients reset?
end

-- we do not need to accumulate parameters when sharing
ParamAccess.sharedAccUpdateGradParameters = ParamAccess.accUpdateGradParameters


function ParamAccess:__tostring__()
    return torch.type(self) ..
            "TODO"
end

-- functions for aggregating gradients over param access modules
local function getOrCreateValue(parent, path, index, dims)
    print("----")
    print(parent)
    print(path[index])
    if (path[index] == nil) then
        return parent
    else
        if parent[path[index]] == nil then
            if torch.type(dims[path[index]]) == "torch.LongStorage" then
                parent[path[index]] = torch.Tensor(dims[path[index]]):zero()
            else
                parent[path[index]] = {}
            end
        end
        return getOrCreateValue(parent[path[index]], path, index + 1, dims[path[index]])
    end
end

function wolfe.aggregateGradients(paramAccessors)
    local result
    for i = 1, #paramAccessors do
        local pa = paramAccessors[i]
        if result == nil then
            if torch.type(pa.dims) == "torch.LongStorage" then
                result = torch.Tensor(pa.dims):zero()
            else
                result = {}
            end
        end
        print("Gradient")
        print(pa.gradWeight)
        print("Result")
        print(result)
        print("Path")
        print(pa.currentPath)
        print("dims")
        print(pa.dims)
        --get or create path until pa.currentPath
        local target = getOrCreateValue(result, pa.currentPath, 1, pa.dims)
        print("Target")
        print(target)

        addNestedTable(target, 1, pa.gradWeight)
    end
    print(result[1][1])
    print(result[2][1])

    return result
end



